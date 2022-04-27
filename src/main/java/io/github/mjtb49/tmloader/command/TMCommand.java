package io.github.mjtb49.tmloader.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.io.*;
import java.util.HashMap;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.minecraft.block.FacingBlock.FACING;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
public class TMCommand {

    private static final int addressOnDy = 7;
    private static final int addressOffDy = 8;
    private static final int outputDy = 3;
    //note zCorrection[zCorrection[z]] = z.
    private static final int[] zCorrection = {0,2,1,3};

    public static BlockPos getPosOfAddress(int addressId, boolean readOne) {
        //one being read should give a negative z coordinate
        int zUntransformed = addressId & 0b11;
        addressId >>>= 2;
        int yUntransformed = addressId & 0b111;
        addressId >>>=3;
        int xUntransformed = addressId;
        int blockX = xUntransformed - 128;
        int chunkY = yUntransformed + 4;
        int chunkZ = zCorrection[zUntransformed] + 2;
        if (readOne) {
            chunkZ = -chunkZ - 1;
            return new BlockPos(blockX, 16 * chunkY + outputDy, chunkZ * 16 + 1);
        } else {
            return new BlockPos(blockX, 16 * chunkY + outputDy, chunkZ * 16);
        }
    }

    public static int getAddressId(int blockX, int chunkY, int chunkZ) {

        // x in 0-255 inc
        blockX = blockX + 128;
        //get y in 0-7 inc.
        chunkY = chunkY - 4;
        //get z in 0-3 inc. Also handle that addresses are split between + and -
        if (chunkZ < 0) {
            chunkZ = -chunkZ - 1;
        }
        chunkZ = chunkZ - 2;
        chunkZ = zCorrection[chunkZ];

        //attempting to prevent update suppression by spacing out adjacent addresses, probably won't work
        return blockX * (8 * 4) + chunkY * 4 + chunkZ;
    }

    private static void writeBitState(ServerWorld sw, int toWrite, BlockPos pos) {
        for (int i = 0; i < 15; i++) {
            if ((toWrite & 1) == 1) {
                sw.setBlockState(pos.add(0,0,i), Blocks.OBSERVER.getDefaultState().with(FACING, Direction.UP));
            } else {
                sw.setBlockState(pos.add(0,0,i), Blocks.SANDSTONE.getDefaultState());
            }
            toWrite >>>= 1;
        }
    }

    private static void writeHalt(ServerWorld sw) {
        writeBitState(sw, 0, getPosOfAddress(0,false));
        writeBitState(sw, 0, getPosOfAddress(0,true));

        //TODO fix redstone so these extra states aren't needed
        //Currently they need to output 0 since they get fired by accident
        for (int i = 1; i < 4; i++) {
            writeBitState(sw, 0, getPosOfAddress(i, false));
            writeBitState(sw, 0, getPosOfAddress(i, true));
        }
    }

    private static void writeErrorState(ServerWorld sw, int address) {
        if (address == (1 << 13) - 1) {
            writeBitState(sw, 0, getPosOfAddress(address, false));
            writeBitState(sw, 0, getPosOfAddress(address, true));
        } else {
            writeBitState(sw, ((1 << 13) - 1) << 1, getPosOfAddress(address, false));
            writeBitState(sw, ((1 << 13) - 1) << 1, getPosOfAddress(address, true));
        }
    }

    private static void writeStates(ServerWorld sw, HashMap<String,Integer> addresses, HashMap<String,String> states, int numStates) {
        writeHalt(sw);
        for (String s: states.keySet()) {
            int address = addresses.get(s);
            String[] stateInstructions = states.get(s).trim().split(" ");

            //write state for if we read 0
            BlockPos pos = getPosOfAddress(address, false);
            int toWrite = Integer.parseInt(stateInstructions[0]);
            if (addresses.containsKey(stateInstructions[2]))
                toWrite += addresses.get(stateInstructions[2]) << 1;
            else {
                toWrite += (1L << 14) - 2;
                sw.getPlayers().get(0).sendMessage(new LiteralText("Warning, state " + s + " errors with code " + stateInstructions[2] + " if 0 is read"), false);
            }
            if (stateInstructions[1].equals("L"))
                toWrite += 1 << 14;
            writeBitState(sw, toWrite, pos);

            //write states if we read 1
            pos = getPosOfAddress(address, true);
            toWrite = Integer.parseInt(stateInstructions[3]) ^ 1;
            if (addresses.containsKey(stateInstructions[5]))
                toWrite += addresses.get(stateInstructions[5]) << 1;
            else {
                toWrite += (1L << 14) - 2;
                sw.getPlayers().get(0).sendMessage(new LiteralText("Warning, state " + s + " errors with code " + stateInstructions[5] + " if 1 is read"), false);
            }
            if (stateInstructions[4].equals("L"))
                toWrite += 1 << 14;
            writeBitState(sw, toWrite, pos);
        }

        for (int i = numStates + 1; i < 1 << 13; i++) {
            writeErrorState(sw, i);
        }
    }

    private static void setAddress(ServerWorld sw, int blockX, int chunkY, int chunkZ) {
        int localId = getAddressId(blockX,chunkY,chunkZ) / 4;
        int dz;
        if (chunkZ < 0) {
            dz = 3;
        } else {
            dz = 2;
        }
        int index = 0;
        while (index < 11) {
            if ((localId & 1) == 1) {
                sw.setBlockState(new BlockPos(blockX, chunkY * 16 + addressOnDy, chunkZ * 16 + dz), Blocks.REDSTONE_BLOCK.getDefaultState());
                sw.setBlockState(new BlockPos(blockX, chunkY * 16 + addressOffDy, chunkZ * 16 + dz), Blocks.AIR.getDefaultState());
            } else {
                sw.setBlockState(new BlockPos(blockX, chunkY * 16 + addressOffDy, chunkZ * 16 + dz), Blocks.REDSTONE_BLOCK.getDefaultState());
                sw.setBlockState(new BlockPos(blockX, chunkY * 16 + addressOnDy, chunkZ * 16 + dz), Blocks.AIR.getDefaultState());
            }
            dz++;
            index++;
            localId >>>= 1;
        }
    }

    private static void resetSubchunk(ServerWorld sw, int x, int y, int z) {

        //reset the outputs
        for (int dx = 0; dx <= 15; dx ++) {
            for (int dz = 0; dz <= 14; dz++) {
                BlockPos pos;
                if (z < 0) {
                    pos = new BlockPos(x*16 + dx, y * 16 + outputDy, z * 16 + dz + 1);
                } else {
                    pos = new BlockPos(x*16 + dx, y * 16 + outputDy, z * 16 + dz);
                }
                sw.setBlockState(pos, Blocks.OBSERVER.getDefaultState().with(FACING, Direction.UP));
            }
        }

        //reset the lock line
        for (int dx = 0; dx <= 15; dx ++) {
            int dz;
            if (z > 0) {
                dz = 1;
            } else {
                dz = 14;
            }
            sw.setBlockState(new BlockPos(x * 16 + dx, y * 16 + addressOnDy, z * 16 + dz), Blocks.REDSTONE_BLOCK.getDefaultState());
            sw.setBlockState(new BlockPos(x * 16 + dx, y * 16 + addressOffDy, z * 16 + dz), Blocks.AIR.getDefaultState());
        }

        //resetAddresses
        for (int dx = 0; dx <= 15; dx ++) {
            setAddress(sw, 16 * x + dx, y, z);
        }

    }

    private static void resetTM(ServerWorld sw) {
        //-z brick
        for (int x = -8; x <= 7; x++)
            for (int y = 4; y <= 11; y++)
                for (int z = -6; z <= -3; z++) {
                    resetSubchunk(sw,x,y,z);
                }
        //+z brick
        for (int x = -8; x <= 7; x++)
            for (int y = 4; y <= 11; y++)
                for (int z = 2; z <= 5; z++) {
                    resetSubchunk(sw,x,y,z);
                }
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("turing").then(
                        literal("reset").executes(c -> {
                            ServerWorld sw = c.getSource().getWorld();
                            resetTM(sw);
                            return 1;
                        })
                ).then(
                        literal("load").then(
                                argument("path", string()).executes(
                                        c -> {
                                            try (BufferedReader br = new BufferedReader(new FileReader(getString(c, "path")))) {
                                                HashMap<String, Integer> addresses = new HashMap<>();
                                                HashMap<String, String> states = new HashMap<>();

                                                addresses.put("HALT", 0);
                                                //TODO redo machine so that I can store actual instructions in 1-3
                                                //currently these states fire accidentally often, so they need to
                                                //never output.
                                                addresses.put("BOLAN_ERROR1", 1);
                                                addresses.put("BOLAN_ERROR2", 2);
                                                addresses.put("BOLAN_ERROR3", 3);
                                                //load the states
                                                String line;
                                                //TODO don't forget to change id once the redstone is fixed
                                                int id = 4;
                                                while ((line = br.readLine()) != null) {
                                                    String[] pair = line.split("=");
                                                    String stateName = pair[0].trim();
                                                    states.put(stateName, pair[1].trim());
                                                    addresses.put(stateName, id);
                                                    id++;
                                                }

                                                writeStates(c.getSource().getWorld(), addresses, states, id);

                                            } catch (FileNotFoundException fileNotFoundException) {
                                                c.getSource().getPlayer().sendMessage(new LiteralText("Could not find file"), false);
                                                fileNotFoundException.printStackTrace();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                            return 1;
                                        }
                                )
                        )
                )
        );
    }
}
