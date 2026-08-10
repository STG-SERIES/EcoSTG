package com.ecostg.paper.job;

import org.bukkit.Material;

import java.util.List;

public record JobDefinition(
        String id,
        String displayName,
        Material icon,
        List<String> description,
        Material requiredMaterial,
        int requiredAmount
) {
}
