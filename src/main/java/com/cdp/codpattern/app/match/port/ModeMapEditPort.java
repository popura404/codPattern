package com.cdp.codpattern.app.match.port;

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

    boolean supportsObjectFeature(String featureKey);

    Optional<ModeObjectData> objectFeature(String featureKey);

    void setObjectFeature(String featureKey, ModeObjectData objectData);
}
