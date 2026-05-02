package com.cdp.codpattern.app.match.editor;

import java.util.List;
import java.util.Optional;

public interface ModeMapEditorSchema {
    List<PointLayerDefinition> pointLayers();

    List<AreaLayerDefinition> areaLayers();

    List<ObjectFeatureDefinition> objectFeatures();

    default Optional<PointLayerDefinition> pointLayer(String key) {
        return pointLayers().stream()
                .filter(layer -> layer.key().equals(key))
                .findFirst();
    }

    default boolean supportsPointLayer(String key) {
        return pointLayer(key).isPresent();
    }

    default Optional<AreaLayerDefinition> areaLayer(String key) {
        return areaLayers().stream()
                .filter(layer -> layer.key().equals(key))
                .findFirst();
    }

    default boolean supportsAreaLayer(String key) {
        return areaLayer(key).isPresent();
    }

    default boolean supportsObjectFeature(String key) {
        return objectFeatures().stream()
                .anyMatch(feature -> feature.key().equals(key));
    }
}
