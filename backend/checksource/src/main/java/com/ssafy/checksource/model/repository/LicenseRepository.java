package com.ssafy.checksource.model.repository;

import java.util.List;

import org.hibernate.type.TrueFalseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ssafy.checksource.model.entity.License;
import com.ssafy.checksource.model.entity.LicenseOpensource;


public interface LicenseRepository extends JpaRepository<License, Long>{
	
	public Page<License> findByNameLike(String name, Pageable pageable);
    public Page<License> findByIdentifierLike(String identifier, Pageable pageable);
    public License findByLicenseId(long licenseId);
	public List<License> findByNameLike(String lincenseName);
    
	//페이징 포함된
	@Query(value = "select * from license where license_id in " + 
			"(select distinct license_id from license_opensource where opensource_id " + 
			"in (select opensource_id from opensource_project where project_id = ?1)) " + 
			"order by contents asc",
			countQuery = "(select count(distinct license_id) from license_opensource where opensource_id " + 
					"in (select opensource_id from opensource_project where project_id = ?1)) ",
			nativeQuery = true)
	public Page<License> findAllByProjectId (Long projectId, Pageable page);
	
	//페이징 없는
	@Query(value = "select * from license where license_id in " + 
			"(select distinct license_id from license_opensource where opensource_id " + 
			"in (select opensource_id from opensource_project where project_id = ?1)) " + 
			"order by contents asc",
			nativeQuery = true)
	public List<License> findCountByProjectId (Long projectId);
	
	
	//부서별 라이선스 종류의 수
	@Query(value = "select * from license where license_id in "+
			"(select distinct license_id from license_opensource where opensource_id in " + 
			"(select distinct opensource_id from opensource_project where project_id in " + 
			"(select project_id from project where depart_id = ?1))) ", nativeQuery = true)
	public List<License> findByDepart (Long departId);
}
