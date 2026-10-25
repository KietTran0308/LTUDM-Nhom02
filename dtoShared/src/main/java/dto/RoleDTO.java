package dto;

import java.io.Serializable;

public class RoleDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long roleID;
    private String nameRole;

    public RoleDTO() {}

    public RoleDTO(Long roleID, String nameRole) {
        this.roleID = roleID;
        this.nameRole = nameRole;
    }

    public Long getRoleID() { return roleID; }
    public void setRoleID(Long roleID) { this.roleID = roleID; }

    public String getNameRole() { return nameRole; }
    public void setNameRole(String nameRole) { this.nameRole = nameRole; }

    @Override
    public String toString() {
        return nameRole;
    }
}