package me.nexthub.sool.notify;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.common.Paged;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private final Auth auth;
    private final NotificationService notifications;

    public NotificationController(Auth auth, NotificationService notifications) {
        this.auth = auth;
        this.notifications = notifications;
    }

    @GetMapping("/api/notifications")
    public Paged<NotificationService.Notification> list(@RequestParam(name = "page", required = false) Integer page,
                                                        HttpServletRequest request) {
        return notifications.list(auth.requireUser(request).id(), Paged.page(page), 20);
    }

    @PostMapping("/api/notifications/read-all")
    public Map<String, Boolean> readAll(HttpServletRequest request) {
        notifications.markRead(auth.requireUser(request).id(), null);
        return Map.of("ok", true);
    }

    @PostMapping("/api/notifications/{id}/read")
    public Map<String, Boolean> read(@PathVariable("id") long id, HttpServletRequest request) {
        notifications.markRead(auth.requireUser(request).id(), id);
        return Map.of("ok", true);
    }
}
