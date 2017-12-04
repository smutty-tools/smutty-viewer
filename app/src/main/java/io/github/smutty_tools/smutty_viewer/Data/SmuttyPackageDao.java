package io.github.smutty_tools.smutty_viewer.Data;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;

@Dao
public interface SmuttyPackageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public void insert(SmuttyPackage... packages);
}
