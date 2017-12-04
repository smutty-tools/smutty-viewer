package io.github.smutty_tools.smutty_viewer.Data;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

@Database(entities = {SmuttyPackage.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract SmuttyPackageDao smuttyPackageDao();
}
