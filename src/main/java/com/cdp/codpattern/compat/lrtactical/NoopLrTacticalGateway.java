package com.cdp.codpattern.compat.lrtactical;

public final class NoopLrTacticalGateway implements LrTacticalGateway {
    @Override
    public boolean isThrowableEnabled() {
        return true;
    }
}
