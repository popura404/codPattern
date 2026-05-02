package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.editor.ModeAreaData;
import com.cdp.codpattern.app.match.editor.ModeObjectData;
import com.cdp.codpattern.app.match.editor.ModePointData;

import java.util.List;
import java.util.Optional;

public interface ModeMapEditPort {
    boolean supportsPointLayer(String layerKey);

    List<ModePointData> pointLayerPoints(String teamName, String layerKey);

    boolean addPointLayerPoint(String teamName, ModePointData point);

    Optional<ModePointData> removePointLayerPoint(String teamName, String layerKey, int index);

    void replacePointLayerPoints(String teamName, String layerKey, List<ModePointData> points);

    default int clearPointLayerPoints(String teamName, String layerKey) {
        int removedCount = pointLayerPoints(teamName, layerKey).size();
        replacePointLayerPoints(teamName, layerKey, List.of());
        return removedCount;
    }

    default boolean supportsAreaLayer(String layerKey) {
        return false;
    }

    default List<ModeAreaData> areaLayerAreas(String layerKey) {
        return List.of();
    }

    default boolean addAreaLayerArea(ModeAreaData area) {
        return false;
    }

    default Optional<ModeAreaData> removeAreaLayerArea(String layerKey, int index) {
        return Optional.empty();
    }

    default void replaceAreaLayerAreas(String layerKey, List<ModeAreaData> areas) {
    }

    default int clearAreaLayerAreas(String layerKey) {
        int removedCount = areaLayerAreas(layerKey).size();
        replaceAreaLayerAreas(layerKey, List.of());
        return removedCount;
    }

    boolean supportsObjectFeature(String featureKey);

    Optional<ModeObjectData> objectFeature(String featureKey);

    void setObjectFeature(String featureKey, ModeObjectData objectData);
}
