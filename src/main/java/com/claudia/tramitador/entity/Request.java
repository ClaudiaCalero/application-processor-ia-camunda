package com.claudia.tramitador.entity;

import com.claudia.tramitador.enums.RequestStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class Request {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @Column(length = 5000)
    private String text;

    private String category;
    private Double score;

    @Column(length = 2000)
    private String summary;

    private Double amount;
    private boolean reviewedByPerson;

    @Enumerated(EnumType.STRING)
    private RequestStatus status = RequestStatus.RECEIVED;

    private Instant createdAt = Instant.now();

    public Request() {
    }
    public Request(String text) {
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public boolean isReviewedByPerson() {
        return reviewedByPerson;
    }

    public void setReviewedByPerson(boolean reviewedByPerson) {
        this.reviewedByPerson = reviewedByPerson;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
