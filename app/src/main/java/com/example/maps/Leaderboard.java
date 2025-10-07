package com.example.maps;

import android.app.Application;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Leaderboard extends Application {
    private int holeCount = 0;

    public int getHoleCount() {
        return holeCount;
    }

    public void setHoleCount(int holeCount) {
        this.holeCount = holeCount;
    }

    // Use the line
    // int holeCount = ((Leaderboard) getApplication()).updateHoleCount();
    // to update the number of holes opened by this particular user.
    public int updateHoleCount() {
        int lines = 0;
        File file = new File(getFilesDir(), "hole_coordinates");

        if (!file.exists()) {
            setHoleCount(0);
            return 0;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (reader.readLine() != null) {
                lines++;
            }
        } catch (IOException e) {
            Log.e("Error reading hole_coordinates file", String.valueOf(e));
        }

        setHoleCount(lines);
        return lines;
    }
}