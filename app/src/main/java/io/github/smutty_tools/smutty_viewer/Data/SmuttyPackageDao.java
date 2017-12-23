package io.github.smutty_tools.smutty_viewer.Data;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

@Dao
public interface SmuttyPackageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SmuttyPackage... packages);

    @Query("DELETE FROM packages")
    void truncate();
}
