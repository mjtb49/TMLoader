package io.github.mjtb49.tmloader.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TMCommandTest {
    @Test
    public void confirmPositionToId() {
        boolean allWorked = true;
        for (int i = 0; i < 1L << 13; i++) {
            allWorked &= i == (TMCommand.getAddressId(TMCommand.getPosOfAddress(i, true).getX(), TMCommand.getPosOfAddress(i, true).getY()>>4, TMCommand.getPosOfAddress(i, true).getZ()>>4));
            allWorked &= i == (TMCommand.getAddressId(TMCommand.getPosOfAddress(i, false).getX(), TMCommand.getPosOfAddress(i, false).getY()>>4, TMCommand.getPosOfAddress(i, false).getZ()>>4));
        }
        assertTrue(allWorked);
    }
}