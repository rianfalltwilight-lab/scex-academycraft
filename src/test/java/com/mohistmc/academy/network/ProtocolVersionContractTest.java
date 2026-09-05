package com.mohistmc.academy.network;
import com.mohistmc.academy.AcademyCraft;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProtocolVersionContractTest {
 @Test void protocolNamesPayloadAndDataGeneration(){assertEquals("academy-1.21.1-payload-v14-data-v4",AcademyCraft.NETWORK_PROTOCOL);}
}
