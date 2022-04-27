package io.github.mjtb49.tmloader.command;

import net.minecraft.text.LiteralText;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TMTest {
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";

    @Test
    public void printExpectedTMOutput() {
        String path = "C:\\Users\\Matthew\\goldbach.txt";
        String entryStateName = "000";
        //String path = "C:\\Users\\Matthew\\riemann.txt";
        //String entryStateName = "!ENTRY";
        //String path = "C:\\Users\\Matthew\\zf2.txt";
        //String entryStateName = "!ENTRY";
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
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

            String tape = "0";
            String currentState = entryStateName;
            int cursor = 0;
            for (int i = 0; i < 200; i++) {
                StringBuilder consoleOutput = new StringBuilder(tape);
                consoleOutput.insert(cursor, '\u0332');
                //consoleOutput.insert(cursor + 2, );
                System.out.println("Step: " + i + "  State: " + currentState + "  Address: " + addresses.get(currentState) +  "\n" + consoleOutput);
                //System.out.println(tape);
                String[] instructions = states.get(currentState).split(" ");
                int r = 0;
                if (tape.charAt(cursor) == '1') {
                    r=3;
                }

                StringBuilder sb = new StringBuilder(tape);
                if (instructions[r].trim().equals("0")) {
                    sb.setCharAt(cursor, '0');
                } else {
                    sb.setCharAt(cursor, '1');
                }
                tape = sb.toString();
                currentState = instructions[r+2].trim();
                if (instructions[r + 1].equals("L")) {
                    cursor--;
                } else {
                    cursor++;
                }
                if (cursor < 0) {
                    tape = "0" + tape;
                    cursor++;
                } else if (cursor == tape.length()) {
                    tape = tape + "0";
                }
            }

        } catch (IOException fileNotFoundException) {
            fileNotFoundException.printStackTrace();
        }
    }
}
