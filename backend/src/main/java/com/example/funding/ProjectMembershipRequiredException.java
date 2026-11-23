package com.example.funding;

public class ProjectMembershipRequiredException extends IllegalStateException {
    public ProjectMembershipRequiredException() {
        super("当前用户未加入该项目");
    }
}