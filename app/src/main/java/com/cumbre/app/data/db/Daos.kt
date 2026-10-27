package com.cumbre.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RouteDao {
    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    abstract fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    abstract fun observe(id: Long): Flow<RouteEntity?>

    @Query("SELECT * FROM routes WHERE id = :id")
    abstract suspend fun get(id: Long): RouteEntity?

    @Query("SELECT * FROM routes WHERE fingerprint = :fingerprint LIMIT 1")
    abstract suspend fun findByFingerprint(fingerprint: String): RouteEntity?

    @Query("SELECT * FROM routes WHERE lastOpenedAt IS NOT NULL ORDER BY lastOpenedAt DESC LIMIT 1")
    abstract fun observeMostRecent(): Flow<RouteEntity?>

    @Query("SELECT * FROM routes")
    abstract suspend fun getAll(): List<RouteEntity>

    @Insert
    abstract suspend fun insert(route: RouteEntity): Long

    @Update
    abstract suspend fun update(route: RouteEntity)

    @Query("UPDATE routes SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("UPDATE routes SET favorite = :favorite WHERE id = :id")
    abstract suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE routes SET lastOpenedAt = :time WHERE id = :id")
    abstract suspend fun markOpened(id: Long, time: Long)

    @Query("DELETE FROM routes WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM routes")
    abstract suspend fun deleteAll()

    @Insert
    abstract suspend fun insertWaypoints(waypoints: List<WaypointEntity>)

    @Query("SELECT * FROM waypoints WHERE routeId = :routeId ORDER BY position")
    abstract suspend fun waypoints(routeId: Long): List<WaypointEntity>

    @Transaction
    open suspend fun insertWithWaypoints(route: RouteEntity, waypoints: List<WaypointEntity>): Long {
        val id = insert(route)
        if (waypoints.isNotEmpty()) insertWaypoints(waypoints.map { it.copy(routeId = id) })
        return id
    }
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE state = 'FINISHED' ORDER BY startedAt DESC")
    fun observeFinished(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE id = :id")
    fun observe(id: Long): Flow<ActivityEntity?>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun get(id: Long): ActivityEntity?

    @Query("SELECT * FROM activities WHERE state != 'FINISHED' ORDER BY startedAt DESC LIMIT 1")
    suspend fun findOpen(): ActivityEntity?

    @Insert
    suspend fun insert(activity: ActivityEntity): Long

    @Update
    suspend fun update(activity: ActivityEntity)

    @Query("UPDATE activities SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE activities SET savedRouteId = :routeId WHERE id = :id")
    suspend fun setSavedRoute(id: Long, routeId: Long?)

    @Query("UPDATE activities SET savedRouteId = NULL WHERE savedRouteId = :routeId")
    suspend fun clearSavedRoute(routeId: Long)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM activities")
    suspend fun deleteAll()

    @Insert
    suspend fun insertPoint(point: ActivityPointEntity): Long

    @Query("SELECT * FROM activity_points WHERE activityId = :activityId ORDER BY segment, timeMillis")
    suspend fun points(activityId: Long): List<ActivityPointEntity>

    @Query("SELECT COUNT(*) FROM activity_points WHERE activityId = :activityId")
    suspend fun pointCount(activityId: Long): Int

    @Query("SELECT * FROM activities")
    suspend fun getAll(): List<ActivityEntity>
}

@Dao
interface OfflineMapDao {
    @Query("SELECT * FROM offline_maps ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<OfflineMapEntity>>

    @Query("SELECT * FROM offline_maps")
    suspend fun getAll(): List<OfflineMapEntity>

    @Query("SELECT * FROM offline_maps WHERE id = :id")
    suspend fun get(id: Long): OfflineMapEntity?

    @Insert
    suspend fun insert(map: OfflineMapEntity): Long

    @Query("UPDATE offline_maps SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE offline_maps SET problem = :problem WHERE id = :id")
    suspend fun setProblem(id: Long, problem: String?)

    @Query("DELETE FROM offline_maps WHERE id = :id")
    suspend fun delete(id: Long)
}
