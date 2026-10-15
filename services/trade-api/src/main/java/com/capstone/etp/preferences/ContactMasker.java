package com.capstone.etp.preferences;

//Masks contact details for display and logs. 
final class ContactMasker {

    private ContactMasker() {
    }

    /** "priya.menon@example.com" becomes "p***@example.com". */
    static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** "9876543210" becomes "******3210". */
    static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.trim();
        if (digits.length() <= 4) {
            return "****";
        }
        return "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }
}