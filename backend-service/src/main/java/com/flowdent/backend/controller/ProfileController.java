package com.flowdent.backend.controller;

import com.flowdent.backend.dto.Dtos.*;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.Address;
import com.flowdent.backend.model.User;
import com.flowdent.backend.repository.AddressRepository;
import com.flowdent.backend.repository.UserRepository;
import com.flowdent.backend.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final CurrentUser currentUser;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public UserResponse get() {
        return UserResponse.from(currentUser.require());
    }

    @PutMapping
    public UserResponse update(@Valid @RequestBody ProfileUpdateRequest req) {
        User u = currentUser.require();
        if (req.fullName() != null) u.setFullName(req.fullName());
        if (req.phone() != null) u.setPhone(req.phone());
        if (req.address() != null) u.setAddress(req.address());
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            if (req.currentPassword() == null || !passwordEncoder.matches(req.currentPassword(), u.getPassword())) {
                throw ApiException.badRequest("Current password is incorrect");
            }
            u.setPassword(passwordEncoder.encode(req.newPassword()));
        }
        return UserResponse.from(userRepository.save(u));
    }

    @GetMapping("/addresses")
    public List<Address> addresses() {
        return addressRepository.findByUserIdOrderByIdAsc(currentUser.require().getId());
    }

    @PostMapping("/addresses")
    @Transactional
    public ResponseEntity<Address> addAddress(@Valid @RequestBody AddressRequest req) {
        User u = currentUser.require();
        List<Address> existing = addressRepository.findByUserIdOrderByIdAsc(u.getId());
        boolean makeDefault = Boolean.TRUE.equals(req.isDefault()) || existing.isEmpty();
        if (makeDefault) clearDefault(existing);
        Address a = Address.builder().user(u).build();
        apply(a, req);
        a.setIsDefault(makeDefault);
        return ResponseEntity.status(HttpStatus.CREATED).body(addressRepository.save(a));
    }

    @PutMapping("/addresses/{id}")
    @Transactional
    public Address updateAddress(@PathVariable Long id, @Valid @RequestBody AddressRequest req) {
        User u = currentUser.require();
        Address a = addressRepository.findByIdAndUserId(id, u.getId()).orElseThrow(() -> ApiException.notFound("Address"));
        apply(a, req);
        if (Boolean.TRUE.equals(req.isDefault())) {
            clearDefault(addressRepository.findByUserIdOrderByIdAsc(u.getId()));
            a.setIsDefault(true);
        }
        return addressRepository.save(a);
    }

    @DeleteMapping("/addresses/{id}")
    @Transactional
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id) {
        User u = currentUser.require();
        Address a = addressRepository.findByIdAndUserId(id, u.getId()).orElseThrow(() -> ApiException.notFound("Address"));
        boolean wasDefault = Boolean.TRUE.equals(a.getIsDefault());
        addressRepository.delete(a);
        addressRepository.flush();
        if (wasDefault) {
            addressRepository.findByUserIdOrderByIdAsc(u.getId()).stream().findFirst().ifPresent(next -> {
                next.setIsDefault(true);
                addressRepository.save(next);
            });
        }
        return ResponseEntity.noContent().build();
    }

    private void clearDefault(List<Address> list) {
        list.stream().filter(x -> Boolean.TRUE.equals(x.getIsDefault())).forEach(x -> {
            x.setIsDefault(false);
            addressRepository.save(x);
        });
    }

    private static void apply(Address a, AddressRequest req) {
        a.setLabel(req.label());
        a.setFullAddress(req.fullAddress());
        a.setCity(req.city());
        a.setPostalCode(req.postalCode());
    }
}
