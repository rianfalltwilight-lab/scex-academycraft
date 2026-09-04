package com.mohistmc.academy.network;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FinalProtocolScopeTest {
    private static final UUID CASTER=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PET=UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test void exactLocationSnapshotRejectsIndexShiftAndCoordinateOrNameMutation(){
        var original=LocationConsentScope.LocationSnapshot.of(2,"home","minecraft:overworld",1,64,3);
        assertNotEquals(original,LocationConsentScope.LocationSnapshot.of(1,"home","minecraft:overworld",1,64,3));
        assertNotEquals(original,LocationConsentScope.LocationSnapshot.of(2,"other","minecraft:overworld",1,64,3));
        assertNotEquals(original,LocationConsentScope.LocationSnapshot.of(2,"home","minecraft:overworld",1.0001,64,3));
    }

    @Test void consentScopeRejectsPetAddRemoveOwnerAndRidingEdgeMutation(){
        var location=LocationConsentScope.LocationSnapshot.of(0,"home","minecraft:overworld",1,64,3);
        var caster=new LocationConsentScope.EntitySnapshot(CASTER,"minecraft:player",null);
        var passenger=new LocationConsentScope.EntitySnapshot(PLAYER,"minecraft:player",null);
        var pet=new LocationConsentScope.EntitySnapshot(PET,"minecraft:wolf",CASTER);
        var base=new LocationConsentScope(CASTER,location,Set.of(caster,passenger),Set.of());
        assertNotEquals(base,new LocationConsentScope(CASTER,location,Set.of(caster,passenger,pet),Set.of()));
        assertNotEquals(base,new LocationConsentScope(CASTER,location,Set.of(caster,passenger),
                Set.of(new LocationConsentScope.RidingEdge(PLAYER,CASTER))));
        assertNotEquals(new LocationConsentScope(CASTER,location,Set.of(caster,passenger,pet),Set.of()),
                new LocationConsentScope(CASTER,location,Set.of(caster,passenger,
                        new LocationConsentScope.EntitySnapshot(PET,"minecraft:wolf",PLAYER)),Set.of()));
    }
}
