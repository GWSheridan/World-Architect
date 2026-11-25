package com.gwsheridan.worldsunwritten.commands;

import com.gwsheridan.worldsunwritten.cells.CellCommands;
import com.gwsheridan.worldsunwritten.diagnostics.DiagnosticCommands;
import com.gwsheridan.worldsunwritten.environment.EnvironmentCommands;
import com.gwsheridan.worldsunwritten.inspection.InspectorCommands;
import com.gwsheridan.worldsunwritten.modpack.ModpackCommands;
import com.gwsheridan.worldsunwritten.modpack.PresetStudioCommands;
import com.gwsheridan.worldsunwritten.rules.RuleCommands;
import com.gwsheridan.worldsunwritten.skyblock.AdministrationPanel;
import com.gwsheridan.worldsunwritten.skyblock.IslandAdministration;
import com.gwsheridan.worldsunwritten.skyblock.IslandCaptures;
import com.gwsheridan.worldsunwritten.skyblock.IslandSelections;
import com.gwsheridan.worldsunwritten.structure.AssemblyStudio;
import com.gwsheridan.worldsunwritten.structure.SiteStudio;
import com.gwsheridan.worldsunwritten.structure.StructureCommands;
import com.gwsheridan.worldsunwritten.workbench.WorkbenchCommands;

public final class AllCommands {
    private AllCommands() {}

    public static void register(CommandRegistration commands) {
        CellCommands.register(commands);
        EnvironmentCommands.register(commands);
        DiagnosticCommands.register(commands);
        InspectorCommands.register(commands);
        WorkbenchCommands.register(commands);
        SiteStudio.register(commands);
        RuleCommands.register(commands);
        IslandAdministration.register(commands);
        IslandSelections.register(commands);
        IslandCaptures.register(commands);
        AdministrationPanel.register(commands);
        StructureCommands.register(commands);
        AssemblyStudio.register(commands);
        ModpackCommands.register(commands);
        PresetStudioCommands.register(commands);
    }
}
