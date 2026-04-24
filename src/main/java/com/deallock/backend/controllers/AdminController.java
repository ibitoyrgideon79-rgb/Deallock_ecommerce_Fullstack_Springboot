package com.deallock.backend.controllers;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.NotificationDispatchService;
import com.deallock.backend.services.NotificationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminController {

    private static final long MAX_UPLOAD_BYTES = 2L * 1024L * 1024L;

    private final DealRepository dealRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationDispatchService notifier;

    public AdminController(DealRepository dealRepository,
                           UserRepository userRepository,
                           NotificationService notificationService,
                           NotificationDispatchService notifier) {
        this.dealRepository = dealRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.notifier = notifier;
    }

    @GetMapping("/admin")
    public String admin(Model model,
                        @RequestParam(value = "message", required = false) String message,
                        @RequestParam(value = "start", required = false) String start,
                        @RequestParam(value = "end", required = false) String end,
                        Principal principal) {
        start = sanitizeDateParam(start);
        end = sanitizeDateParam(end);
        List<Deal> allDeals;
        if ((start != null && !start.isBlank()) || (end != null && !end.isBlank())) {
            ZoneId zone = ZoneId.systemDefault();
            try {
                Instant startInstant = start != null && !start.isBlank()
                        ? LocalDate.parse(start).atStartOfDay(zone).toInstant()
                        : Instant.EPOCH;
                Instant endInstant = end != null && !end.isBlank()
                        ? LocalDate.parse(end).plusDays(1).atStartOfDay(zone).toInstant()
                        : Instant.now().plusSeconds(60L * 60L * 24L * 365L * 10L);
                allDeals = dealRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startInstant, endInstant);
            } catch (Exception ex) {
                allDeals = dealRepository.findAllByOrderByCreatedAtDesc();
            }
        } else {
            allDeals = dealRepository.findAllByOrderByCreatedAtDesc();
        }
        List<Deal> paymentConfirmedDeals = allDeals.stream()
                .filter(d -> "Approved".equalsIgnoreCase(d.getStatus()))
                .filter(d -> "PAID_CONFIRMED".equalsIgnoreCase(d.getPaymentStatus()))
                .toList();
        List<Deal> paymentNotReceivedDeals = allDeals.stream()
                .filter(d -> "Approved".equalsIgnoreCase(d.getStatus()))
                .filter(d -> "NOT_PAID".equalsIgnoreCase(d.getPaymentStatus()))
                .toList();
        List<Deal> securedDeals = allDeals.stream()
                .filter(d -> "Approved".equalsIgnoreCase(d.getStatus()))
                .filter(Deal::isSecured)
                .toList();
        List<Deal> balancePaymentDeals = allDeals.stream()
                .filter(d -> "Approved".equalsIgnoreCase(d.getStatus()))
                .filter(Deal::isSecured)
                .filter(d -> d.getBalancePaymentStatus() == null
                        || !"PAID_CONFIRMED".equalsIgnoreCase(d.getBalancePaymentStatus()))
                .toList();
        List<Deal> deliveryInitiationDeals = allDeals.stream()
                .filter(d -> "Approved".equalsIgnoreCase(d.getStatus()))
                .filter(Deal::isSecured)
                .filter(d -> "PAID_CONFIRMED".equalsIgnoreCase(d.getBalancePaymentStatus()))
                .filter(d -> d.getDeliveryInitiatedAt() == null)
                .toList();
        List<Deal> inTransitDeals = allDeals.stream()
                .filter(d -> "Approved".equalsIgnoreCase(d.getStatus()))
                .filter(d -> d.getDeliveryInitiatedAt() != null)
                .filter(d -> d.getDeliveryConfirmedAt() == null)
                .toList();
        List<Deal> deliveryConfirmationDeals = allDeals.stream()
                .filter(d -> "Approved".equalsIgnoreCase(d.getStatus()))
                .filter(d -> d.getDeliveryInitiatedAt() != null)
                .filter(Deal::isDeliveryConfirmedByUser)
                .filter(d -> d.getDeliveryConfirmedAt() == null)
                .toList();
        List<Deal> feedbackDeals = allDeals.stream()
                .filter(d -> "Approved".equalsIgnoreCase(d.getStatus()))
                .filter(d -> d.getFeedback() != null && !d.getFeedback().isBlank())
                .toList();
        List<Deal> concludedDeals = allDeals.stream()
                .filter(d -> "Approved".equalsIgnoreCase(d.getStatus()))
                .filter(d -> d.getDeliveryConfirmedAt() != null)
                .toList();

        model.addAttribute("pendingDeals", allDeals.stream()
                .filter(d -> d.getStatus() == null || "Pending Approval".equalsIgnoreCase(d.getStatus()))
                .toList());
        model.addAttribute("approvedDeals", allDeals.stream()
                .filter(d -> "Approved".equalsIgnoreCase(d.getStatus()))
                .toList());
        model.addAttribute("rejectedDeals", allDeals.stream()
                .filter(d -> "Rejected".equalsIgnoreCase(d.getStatus()))
                .toList());
        model.addAttribute("paymentConfirmedDeals", paymentConfirmedDeals);
        model.addAttribute("paymentNotReceivedDeals", paymentNotReceivedDeals);
        model.addAttribute("securedDeals", securedDeals);
        model.addAttribute("balancePaymentDeals", balancePaymentDeals);
        model.addAttribute("deliveryInitiationDeals", deliveryInitiationDeals);
        model.addAttribute("inTransitDeals", inTransitDeals);
        model.addAttribute("deliveryConfirmationDeals", deliveryConfirmationDeals);
        model.addAttribute("feedbackDeals", feedbackDeals);
        model.addAttribute("concludedDeals", concludedDeals);
        model.addAttribute("message", message);
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("now", Instant.now());
        if (principal != null) {
            userRepository.findByEmail(principal.getName()).ifPresent(user -> {
                model.addAttribute("currentUser", user);
                model.addAttribute("isAdmin", "ROLE_ADMIN".equals(user.getRole()));
                model.addAttribute("notificationCount", notificationService.countUnread(user));
            });
        }
        return "admindashboard";
    }

    @GetMapping("/admin/payment-proofs")
    public String paymentProofs(Model model, Principal principal) {
        if (principal != null) {
            userRepository.findByEmail(principal.getName()).ifPresent(user -> {
                model.addAttribute("currentUser", user);
                model.addAttribute("isAdmin", "ROLE_ADMIN".equals(user.getRole()));
                model.addAttribute("notificationCount", notificationService.countUnread(user));
            });
        }
        return "payment-proofs";
    }

    @PostMapping("/admin/deals/{id}/approve")
    public String approve(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setStatus("Approved");
            dealRepository.save(deal);
            notifyApproval(deal);
            notifier.notifyUser(deal.getUser(),
                    "Your deal was approved.",
                    "Your Deal Was Approved",
                    "Your deal was approved: " + safe(deal.getTitle()),
                    "Your deal was approved. Please proceed to payment.");
            notifier.notifyAdmins(
                    "Deal approved: " + safe(deal.getTitle()),
                    "Deal Approved",
                    "Deal approved: " + safe(deal.getTitle()),
                    "Deal approved: " + safe(deal.getTitle()));
        });
        return "redirect:/admin?message=approved";
    }

    @PostMapping("/admin/deals/{id}/reject")
    public String reject(@PathVariable("id") Long id,
                         @RequestParam(value = "rejectionReason", required = false) String rejectionReason) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setStatus("Rejected");
            String reason = rejectionReason == null ? "" : rejectionReason.trim();
            if (reason.isBlank()) {
                reason = "No reason provided.";
            }
            deal.setRejectionReason(reason);
            dealRepository.save(deal);
            notifier.notifyUser(deal.getUser(),
                    "Your deal was rejected. Reason: " + safe(reason),
                    "Deal Rejected",
                    "Your deal was rejected: " + safe(deal.getTitle()) + "\nReason: " + safe(reason),
                    "Your deal was rejected. Reason: " + safe(reason));
            notifier.notifyAdmins(
                    "Deal rejected: " + safe(deal.getTitle()),
                    "Deal Rejected",
                    "Deal rejected: " + safe(deal.getTitle()),
                    "Deal rejected: " + safe(deal.getTitle()));
        });
        return "redirect:/admin?message=rejected";
    }

    @PostMapping("/admin/deals/{id}/payment-confirmed")
    public String paymentConfirmed(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setPaymentStatus("PAID_CONFIRMED");
            dealRepository.save(deal);
            notifier.notifyUser(deal.getUser(),
                    "Payment confirmed for your deal.",
                    "Payment Confirmed",
                    "Payment confirmed for your deal: " + safe(deal.getTitle()),
                    "Payment confirmed for your deal.");
            notifier.notifyAdmins(
                    "Payment confirmed: " + safe(deal.getTitle()),
                    "Payment Confirmed",
                    "Payment confirmed: " + safe(deal.getTitle()),
                    "Payment confirmed: " + safe(deal.getTitle()));
        });
        return "redirect:/admin?message=payment-confirmed";
    }

    @PostMapping("/admin/deals/{id}/payment-not-received")
    public String paymentNotReceived(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setPaymentStatus("NOT_PAID");
            dealRepository.save(deal);
            notifier.notifyUser(deal.getUser(),
                    "Payment not received for your deal.",
                    "Payment Not Received",
                    "Payment not received for your deal: " + safe(deal.getTitle()),
                    "Payment not received for your deal.");
            notifier.notifyAdmins(
                    "Payment not received: " + safe(deal.getTitle()),
                    "Payment Not Received",
                    "Payment not received: " + safe(deal.getTitle()),
                    "Payment not received: " + safe(deal.getTitle()));
        });
        return "redirect:/admin?message=payment-not-received";
    }

    @PostMapping("/admin/deals/{id}/secured")
    public String dealSecured(@PathVariable("id") Long id,
                              @RequestParam(value = "securedPhoto") org.springframework.web.multipart.MultipartFile securedPhoto) {
        if (securedPhoto == null || securedPhoto.isEmpty()) {
            return "redirect:/admin?message=secured-photo-required";
        }
        if (securedPhoto.getSize() > MAX_UPLOAD_BYTES) {
            return "redirect:/admin?message=secured-too-large";
        }
        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return "redirect:/admin?message=secured-not-confirmed";
        }
        var deal = dealOpt.get();
        if (deal.getPaymentStatus() == null || !deal.getPaymentStatus().equalsIgnoreCase("PAID_CONFIRMED")) {
            return "redirect:/admin?message=secured-not-confirmed";
        }
        if (!deal.isSecured()) {
            deal.setSecured(true);
            deal.setSecuredAt(Instant.now());
            try {
                deal.setSecuredItemPhoto(securedPhoto.getBytes());
                deal.setSecuredItemPhotoContentType(securedPhoto.getContentType());
            } catch (Exception ex) {
                System.out.println("[WARN] Failed to read secured photo: " + ex.getMessage());
            }
            dealRepository.save(deal);
            notifier.notifyUser(deal.getUser(),
                    "Your deal has been secured.",
                    "Deal Secured",
                    "Your deal has been secured: " + safe(deal.getTitle()),
                    "Your deal has been secured.");
            notifier.notifyAdmins(
                    "Deal secured: " + safe(deal.getTitle()),
                    "Deal Secured",
                    "Deal secured: " + safe(deal.getTitle()),
                    "Deal secured: " + safe(deal.getTitle()));
        }
        return "redirect:/admin?message=secured";
    }

    @PostMapping("/admin/deals/{id}/balance-confirmed")
    public String balanceConfirmed(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setBalancePaymentStatus("PAID_CONFIRMED");
            dealRepository.save(deal);
            notifier.notifyUser(deal.getUser(),
                    "Balance payment confirmed for your deal.",
                    "Balance Payment Confirmed",
                    "Balance payment confirmed for your deal: " + safe(deal.getTitle()),
                    "Balance payment confirmed for your deal.");
            notifier.notifyAdmins(
                    "Balance payment confirmed: " + safe(deal.getTitle()),
                    "Balance Payment Confirmed",
                    "Balance payment confirmed: " + safe(deal.getTitle()),
                    "Balance payment confirmed: " + safe(deal.getTitle()));
        });
        return "redirect:/admin?message=balance-confirmed";
    }

    @PostMapping("/admin/deals/{id}/delivery-initiated")
    public String deliveryInitiated(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setDeliveryInitiatedAt(Instant.now());
            dealRepository.save(deal);
            notifier.notifyUser(deal.getUser(),
                    "Delivery initiated for your deal.",
                    "Delivery Initiated",
                    "Delivery initiated for your deal: " + safe(deal.getTitle()),
                    "Delivery initiated for your deal.");
            notifier.notifyAdmins(
                    "Delivery initiated: " + safe(deal.getTitle()),
                    "Delivery Initiated",
                    "Delivery initiated: " + safe(deal.getTitle()),
                    "Delivery initiated: " + safe(deal.getTitle()));
        });
        return "redirect:/admin?message=delivery-initiated";
    }

    @PostMapping("/admin/deals/{id}/delivery-confirmed")
    public String deliveryConfirmed(@PathVariable("id") Long id) {
        dealRepository.findById(id).ifPresent(deal -> {
            deal.setDeliveryConfirmedAt(Instant.now());
            dealRepository.save(deal);
            notifier.notifyUser(deal.getUser(),
                    "Delivery confirmed by admin.",
                    "Delivery Confirmation",
                    "Delivery confirmed for your deal: " + safe(deal.getTitle()),
                    "Delivery confirmed for your deal.");
            notifier.notifyAdmins(
                    "Delivery confirmed: " + safe(deal.getTitle()),
                    "Delivery Confirmation",
                    "Delivery confirmed: " + safe(deal.getTitle()),
                    "Delivery confirmed: " + safe(deal.getTitle()));
        });
        return "redirect:/admin?message=delivery-confirmed";
    }

    @PostMapping("/admin/deals/{id}/delete")
    public String delete(@PathVariable("id") Long id,
                         @RequestParam(value = "start", required = false) String start,
                         @RequestParam(value = "end", required = false) String end) {
        dealRepository.deleteById(id);
        if (start != null || end != null) {
            String startParam = start == null ? "" : start;
            String endParam = end == null ? "" : end;
            return "redirect:/admin?message=deleted&start=" + startParam + "&end=" + endParam;
        }
        return "redirect:/admin?message=deleted";
    }

    private void notifyApproval(Deal deal) {
        String details = "Deal approved.\n\nTitle: " + safe(deal.getTitle())
                + "\nClient: " + safe(deal.getClientName())
                + "\nValue: NGN " + (deal.getValue() != null ? deal.getValue() : "0")
                + "\nStatus: " + safe(deal.getStatus());

        if (deal.getUser() != null) {
            notifier.notifyUser(deal.getUser(),
                    "Your deal was approved. Please proceed to payment.",
                    "Your Deal Was Approved",
                    details,
                    "Your deal was approved. Please proceed to payment.");
        }
        notifier.notifyAdmins(
                "A deal has been approved.",
                "Deal Approved",
                details,
                "A deal has been approved.");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String sanitizeDateParam(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }
}
