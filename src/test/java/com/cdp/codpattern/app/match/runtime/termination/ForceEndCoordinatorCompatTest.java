package com.cdp.codpattern.app.match.runtime.termination;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.model.result.ModeOperationResult;
import com.cdp.codpattern.app.match.runtime.lease.ModeMapLeaseRegistry;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Fault injection against the actual coordinator and durable ledger, without a Minecraft client. */
public final class ForceEndCoordinatorCompatTest {
    static final RoomId ROOM = RoomId.of("fixture", "termination");
    public static void main(String[] args) throws Exception {
        orderAndDuplicate(); modeFailuresCannotSkipSharedRecovery(); sharedFailuresAreIndependent();
        staleAndReentrant(); persistenceFailureStillRecovers(); restartNeverReplaysSettlement();
        oldLeaseCannotReleaseNewMatch();
        System.out.println("PASS force-end coordinator: 7 fault/identity/persistence scenarios");
    }
    static ForceEndContext context(ForceEndCoordinator.State s) {
        return new ForceEndContext(ROOM, s.generation, UUID.randomUUID(), "console", "ADMIN_FORCE_END");
    }
    static final class Handler implements ModeForceEndHandler {
        final List<String> calls; String fail;
        Handler(List<String> calls, String fail) { this.calls=calls; this.fail=fail; }
        ModeOperationResult<Void> run(String stage) {
            calls.add(stage);
            if (stage.equals(fail)) throw new IllegalStateException("injected " + stage);
            return ModeOperationResult.success(null);
        }
        public ModeOperationResult<Void> stop(ForceEndContext c) { return run("stop"); }
        public ModeOperationResult<Void> settle(ForceEndContext c) { return run("settlement"); }
        public ModeOperationResult<Void> cleanup(ForceEndContext c) { return run("cleanup"); }
    }
    static void orderAndDuplicate() {
        var coordinator = new ForceEndCoordinator(); var s = new ForceEndCoordinator.State();
        var calls = new ArrayList<String>(); var h = new Handler(calls, ""); var c=context(s);
        var report=coordinator.execute(s,c,h,()->calls.add("entities"),()->calls.add("players"),()->{});
        require(s.terminated && s.complete && report.outcome()==ForceEndCoordinator.Outcome.COMPLETED,"completion");
        require(calls.equals(List.of("stop","settlement","cleanup","entities","players")),"stage order");
        coordinator.execute(s,c,h,()->calls.add("bad"),()->calls.add("bad"),()->{});
        require(calls.size()==5,"duplicate has no effects");
    }
    static void modeFailuresCannotSkipSharedRecovery() {
        for (String stage:List.of("stop","settlement","cleanup")) {
            var s=new ForceEndCoordinator.State(); var calls=new ArrayList<String>(); var h=new Handler(calls,stage);
            var coordinator=new ForceEndCoordinator(); var c=context(s);
            coordinator.execute(s,c,h,()->calls.add("entities"),()->calls.add("players"),()->{});
            require(s.terminated && calls.containsAll(List.of("cleanup","entities","players")),"independent recovery after "+stage);
            require(s.complete==stage.equals("settlement"),"required failures keep room closed");
            long settlements=calls.stream().filter("settlement"::equals).count(); h.fail="";
            coordinator.execute(s,c,h,()->{},()->{},()->{});
            require(s.complete,"retry completes required stages");
            require(settlements==calls.stream().filter("settlement"::equals).count(),"retry never repeats uncertain settlement");
        }
    }
    static void sharedFailuresAreIndependent() {
        var s=new ForceEndCoordinator.State(); var players=new AtomicInteger(); var c=context(s);
        var coordinator=new ForceEndCoordinator(); var h=new Handler(new ArrayList<>(),"");
        coordinator.execute(s,c,h,()->{throw new IllegalStateException("unloaded entity");},players::incrementAndGet,()->{});
        require(players.get()==1 && !s.complete && s.failures.containsKey("entities"),"entity failure must not trap players");
        coordinator.execute(s,c,h,()->{},()->{throw new IllegalStateException("teleport failed");},()->{});
        require(!s.complete && s.failures.containsKey("players") && !s.failures.containsKey("entities"),"accurate retry state");
        coordinator.execute(s,c,h,()->{},()->{},()->{}); require(s.complete,"eventual recovery");
    }
    static void staleAndReentrant() {
        var s=new ForceEndCoordinator.State(); var coordinator=new ForceEndCoordinator(); var calls=new ArrayList<String>();
        var h=new Handler(calls,""); var stale=context(new ForceEndCoordinator.State());
        require(coordinator.execute(s,stale,h,()->{},()->{},()->{}).outcome()==ForceEndCoordinator.Outcome.STALE,"stale rejected");
        require(!s.terminated && calls.isEmpty(),"stale request cannot mutate state");
        var c=context(s);
        coordinator.execute(s,c,h,()->{
            require(s.terminated,"termination authority precedes callbacks");
            require(coordinator.execute(s,c,h,()->{},()->{},()->{}).outcome()==ForceEndCoordinator.Outcome.IN_PROGRESS,"reentrant request isolated");
        },()->{},()->{});
    }
    static void persistenceFailureStillRecovers() {
        var s=new ForceEndCoordinator.State(); var calls=new ArrayList<String>();
        new ForceEndCoordinator().execute(s,context(s),new Handler(calls,""),()->calls.add("entities"),()->calls.add("players"),
                ()->{throw new java.io.IOException("disk full");});
        require(s.terminated && !s.complete && !calls.contains("settlement"),"persistence failure stays pending without settlement");
        require(calls.containsAll(List.of("cleanup","entities","players")),"disk error cannot skip emergency recovery");
        require(s.failures.containsKey("persistence"),"durability failure visible");
    }
    static void restartNeverReplaysSettlement() throws Exception {
        var directory=Files.createTempDirectory("room-recovery-test-"); var path=directory.resolve("recovery.json");
        try {
            var store=new RoomRecoveryStore(path); var data=new RoomRecoveryStore.Data();
            var state=new ForceEndCoordinator.State(); state.active=true; data.rooms.put(ROOM.encode(),state);
            var c=context(state); var calls=new ArrayList<String>(); var h=new Handler(calls, "cleanup");
            new ForceEndCoordinator().execute(state,c,h,()->{},()->{},()->store.write(data));
            var restored=store.read(); var previous=restored.rooms.get(ROOM.encode());
            require(previous.terminated && previous.settlementAttempted && !previous.complete,"restart retains termination and pending work");
            h.fail=""; calls.clear();
            new ForceEndCoordinator().execute(previous,c,h,()->{},()->{},()->store.write(restored));
            require(previous.complete && !calls.contains("settlement"),"restart resumes cleanup without another result");
            Files.writeString(path,"{bad json");
            try {store.read();throw new AssertionError("corrupt ledger accepted");} catch(java.io.IOException expected) { }
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(directory); }
    }
    static void oldLeaseCannotReleaseNewMatch() {
        var leases=new ModeMapLeaseRegistry(); var key=new ModeMapLeaseRegistry.LeaseKey("fixture","arena");
        var old=leases.acquire(key,"match-A").lease();
        require(!leases.acquire(key,"match-B").acquired(),"overlapping occupancy rejected");
        require(leases.release(old),"release A"); var newer=leases.acquire(key,"match-B").lease();
        require(!leases.release(old) && leases.current(key).orElseThrow().equals(newer),"old release cannot affect new match");
    }
    static void require(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
}
