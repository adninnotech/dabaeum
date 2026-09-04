package com.adn.dabaeum.user.application;

import com.adn.dabaeum.user.domain.User;
import java.util.UUID;

public interface UserApplicationService {

    User create(CreateUserCommand command);

    UserPage list(ListUsersQuery query);

    User get(UUID userId);

    User updateProfile(UpdateUserCommand command);

    User changeStatus(ChangeUserStatusCommand command);

    User getMe(UUID currentUserId);

    User updateMe(UUID currentUserId, UpdateMeCommand command);
}
