package com.lanchat.application.dto;

import com.lanchat.domain.model.User;

/**
 * 应用层用户视图，对应契约中的 user / sender 对象：{@code {id, username, nickname}}。
 */
public record UserView(long id, String username, String nickname) {

    public static UserView from(User user) {
        return new UserView(user.id(), user.username(), user.nickname());
    }
}
