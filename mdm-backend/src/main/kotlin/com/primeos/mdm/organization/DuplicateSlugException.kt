package com.primeos.mdm.organization

class DuplicateSlugException(slug: String) : RuntimeException("An organization with slug '$slug' already exists")
