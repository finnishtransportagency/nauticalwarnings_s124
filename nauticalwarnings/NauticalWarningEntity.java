package fi.liikennevirasto.winvis.nauticalwarnings;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.liikennevirasto.winvis.common.CustomLocalDateTimeDeserializer;
import fi.liikennevirasto.winvis.common.CustomLocalDateTimeSerializer;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * This class is holder for Digitraffic Marine Warning
 * Warnings are originally produced as Geojson format, but
 * for STM/WINVIS we convert them to STM are format (S124)
 * and serve as XML for any interested parties.
 */
@Entity
@Table(name = "digitraffic_nautical_warnings")
public class NauticalWarningEntity {

    @Id
    private long id;

    /**
     * Original Digitraffic nautical warning geojson document
     */
    @Column(length = 10485760)
    private String jsonDocument;

    /**
     * Converted S124 format nautical warning document
     */
    @Column(name = "s124_document", length = 10485760)
    private String s124Document;

    /**
     * When this entity was originally created/stored in database
     * Note: Depending on handling, nautical warnings may be recreated
     * everytime poller does polling, instead of being updated, and
     * timestamp may reflect this.
     */
    @JsonSerialize(using = CustomLocalDateTimeSerializer.class)
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    @Column(updatable = false)
    private LocalDateTime createdTime;

    /**
     * When this entity was last updated, or null if it's a new one
     */
    @JsonSerialize(using = CustomLocalDateTimeSerializer.class)
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime updatedTime;

    /**
     * Time when nautical warning was expired. This is set when
     * existing nautical warning id is not returned from Digitraffic API
     * anymore. When this field is set, also the s124 content will be
     * updated to contain the expiration timestamp.
     */
    @JsonSerialize(using = CustomLocalDateTimeSerializer.class)
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime expiredTime;

    /**
     * When message was delivered to SMA, or null for failed delivery
     */
    @JsonSerialize(using = CustomLocalDateTimeSerializer.class)
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime smaDeliveryTime;


    public NauticalWarningEntity() {
        // default constructor
    }

    public NauticalWarningEntity(long id, String jsonDocument, String s124Document) {
        this.id = id;
        this.s124Document = s124Document;
        this.jsonDocument = jsonDocument;
    }

    @PrePersist
    public void prePersist() {
        createdTime = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedTime = LocalDateTime.now();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getS124Document() {
        return s124Document;
    }

    public void setS124Document(String s124Document) {
        this.s124Document = s124Document;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public String getJsonDocument() {
        return jsonDocument;
    }

    public void setJsonDocument(String jsonDocument) {
        this.jsonDocument = jsonDocument;
    }

    public LocalDateTime getExpiredTime() {
        return expiredTime;
    }

    public void setExpiredTime(LocalDateTime expiredTime) {
        this.expiredTime = expiredTime;
    }

    public LocalDateTime getSmaDeliveryTime() {
        return smaDeliveryTime;
    }

    public void setSmaDeliveryTime(LocalDateTime smaDeliveryTime) {
        this.smaDeliveryTime = smaDeliveryTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NauticalWarningEntity that = (NauticalWarningEntity) o;
        return id == that.id &&
                Objects.equals(jsonDocument, that.jsonDocument) &&
                Objects.equals(s124Document, that.s124Document) &&
                Objects.equals(createdTime, that.createdTime) &&
                Objects.equals(updatedTime, that.updatedTime) &&
                Objects.equals(expiredTime, that.expiredTime) &&
                Objects.equals(smaDeliveryTime, that.smaDeliveryTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, jsonDocument, s124Document, createdTime, updatedTime, expiredTime, smaDeliveryTime);
    }

    @Override
    public String toString() {
        return "NauticalWarningEntity{" +
                "id=" + id +
                ", jsonDocument='" + jsonDocument + '\'' +
                ", s124Document='" + s124Document + '\'' +
                ", createdTime=" + createdTime +
                ", updatedTime=" + updatedTime +
                ", expiredTime=" + expiredTime +
                ", smaDeliveryTime=" + smaDeliveryTime +
                '}';
    }
}
