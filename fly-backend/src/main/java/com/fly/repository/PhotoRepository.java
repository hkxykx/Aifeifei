package com.fly.repository;

import com.fly.entity.Photo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    List<Photo> findByAlbumIdOrderBySortAsc(Long albumId);

    long countByAlbumId(Long albumId);

    void deleteByAlbumId(Long albumId);
}
