package me.blueslime.meteor.paper.extras.services.slots;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class SlotHandler {

    public static int fromSize(int size) {
        if (size < 0) {
            return 9;
        }
        if (size < 7) {
            return size * 9;
        }
        if (size > 7) {
            if (size > 50) {
                return 54;
            }
            if (size > 40) {
                return 45;
            }
            if (size > 30) {
                return 36;
            }
            if (size > 20) {
                return 27;
            }
            if (size > 10) {
                return 18;
            }
        }
        return 9;
    }

    public static Collection<Integer> fromBorders(int rows) {
        if (rows < 3) {
            return Collections.emptyList();
        }

        ArrayList<Integer> slots = new ArrayList<>();

        slots.addAll(
                fromRow(0)
        );
        slots.addAll(
                fromRow(rows)
        );
        slots.addAll(
                fromColumn(1, rows * 9)
        );
        slots.addAll(
                fromColumn(9, rows * 9)
        );

        return slots;
    }

    public static Collection<Integer> fromRow(int row) {
        ArrayList<Integer> slots = new ArrayList<>();

        int start = row != 0 ?
                row == 1 ?
                        0 :
                        (row - 1) * 9 :
                0;

        for (int slot = start; slot < start + 9; slot++) {
            slots.add(slot);
        }

        return slots;
    }

    public static Collection<Integer> fromColumn(int column, int slots) {

        ArrayList<Integer> slotList = new ArrayList<>();

        int start = column - 1;

        int end = (slots - 9) + column;

        for (int slot = start; slot <= end; slot += 9) {
            slotList.add(slot);
        }

        return slotList;
    }

}



