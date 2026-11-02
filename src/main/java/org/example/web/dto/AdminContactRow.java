package org.example.web.dto;

public record AdminContactRow(AdminContactDto contact, String ownerName) {

    public AdminContactDto getContact() {
        return contact;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getOwnerDisplay() {
        if (ownerName == null || ownerName.isBlank()) {
            return "ID " + contact.ownerId();
        }
        return ownerName + " (ID " + contact.ownerId() + ")";
    }
}
