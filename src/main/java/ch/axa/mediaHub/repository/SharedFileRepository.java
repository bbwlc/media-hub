package ch.axa.mediaHub.repository;

import ch.axa.mediaHub.model.SharedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SharedFileRepository extends JpaRepository<SharedFile, Long> {

    List<SharedFile> findByOwner(String owner);

    @Query("SELECT CASE WHEN COUNT(s)>0 THEN TRUE ELSE FALSE END FROM SharedFile s WHERE s.owner=:owner AND s.filename=:filename AND s.sharedWith IS NULL")
    boolean existsPublicShare(@Param("owner") String owner, @Param("filename") String filename);

    boolean existsByOwnerAndFilenameAndSharedWith(String owner, String filename, String sharedWith);

    @Query("SELECT CASE WHEN COUNT(s)>0 THEN TRUE ELSE FALSE END FROM SharedFile s WHERE s.owner=:owner AND s.filename=:filename AND (s.sharedWith IS NULL OR s.sharedWith=:viewer)")
    boolean canAccess(@Param("owner") String owner, @Param("filename") String filename, @Param("viewer") String viewer);

    @Query("SELECT s FROM SharedFile s WHERE s.sharedWith=:username OR s.sharedWith IS NULL")
    List<SharedFile> findAccessibleBy(@Param("username") String username);

    @Modifying
    @Transactional
    @Query("DELETE FROM SharedFile s WHERE s.owner=:owner AND s.filename=:filename AND s.sharedWith IS NULL")
    void deletePublicShare(@Param("owner") String owner, @Param("filename") String filename);

    @Modifying
    @Transactional
    void deleteByOwnerAndFilenameAndSharedWith(String owner, String filename, String sharedWith);
}