package com.boutique.pos.security;

import com.boutique.pos.model.AppSection;
import com.boutique.pos.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("sectionAccess")
public class SectionAccessService {

    public boolean check(String section) {
        return checkAny(section);
    }

    public boolean checkAny(String... sections) {
        User user = currentUser();
        if (user == null || user.getRole() == null) return false;
        for (String code : sections) {
            if (user.getRole().getSections().contains(AppSection.valueOf(code))) {
                return true;
            }
        }
        return false;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) return null;
        return (User) auth.getPrincipal();
    }
}
