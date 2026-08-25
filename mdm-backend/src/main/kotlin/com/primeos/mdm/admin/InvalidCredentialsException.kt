package com.primeos.mdm.admin

// Deliberately generic message - never reveal whether the email exists vs
// the password was wrong, since that distinction helps an attacker
// enumerate valid admin accounts.
class InvalidCredentialsException : RuntimeException("Invalid email or password")
