package com.abdullah.msal2.categories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryTemplateRepository extends JpaRepository<CategoryTemplate, String> {

	List<CategoryTemplate> findAllByOrderBySortOrderAsc();
}
