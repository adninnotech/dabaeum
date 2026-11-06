package com.adn.dabaeum.banner.infrastructure.mybatis;

import com.adn.dabaeum.banner.domain.Banner;
import com.adn.dabaeum.banner.domain.BannerRepository;
import com.adn.dabaeum.banner.domain.BannerVisibility;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class BannerMyBatisRepository implements BannerRepository {

    private final BannerMapper mapper;

    public BannerMyBatisRepository(BannerMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Banner banner) {
        mapper.insert(toRow(banner));
    }

    @Override
    public Optional<Banner> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<Banner> findAll() {
        return mapper.selectAll().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean update(Banner banner) {
        return mapper.update(toRow(banner)) == 1;
    }

    @Override
    public boolean softDelete(UUID id, Instant deletedAt) {
        return mapper.softDelete(id, deletedAt) == 1;
    }

    private BannerRow toRow(Banner banner) {
        return new BannerRow(
            banner.id(), banner.badgeText(), banner.title(), banner.subtitle(),
            banner.buttonText(), banner.buttonLink(), banner.visibility().name(),
            banner.startAt(), banner.endAt(), banner.sortOrder(),
            banner.pcImageFileId(), banner.mobileImageFileId(),
            banner.createdAt(), banner.updatedAt(), banner.deletedAt());
    }

    private Banner toDomain(BannerRow row) {
        return new Banner(
            row.id(), row.badgeText(), row.title(), row.subtitle(),
            row.buttonText(), row.buttonLink(), BannerVisibility.valueOf(row.visibility()),
            row.startAt(), row.endAt(), row.sortOrder() == null ? 0 : row.sortOrder(),
            row.pcImageFileId(), row.mobileImageFileId(),
            row.createdAt(), row.updatedAt(), row.deletedAt());
    }
}
