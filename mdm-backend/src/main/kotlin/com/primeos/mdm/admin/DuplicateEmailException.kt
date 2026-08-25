package com.primeos.mdm.admin

class DuplicateEmailException(email: String) : RuntimeException("An admin user with email '$email' already exists")
