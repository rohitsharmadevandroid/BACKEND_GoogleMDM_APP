package com.primeos.mdm.enterprise

import java.util.UUID

class NoPendingGmsSignupException(organizationId: UUID) :
    RuntimeException("No pending GMS signup for organization $organizationId - call startSignup first")
