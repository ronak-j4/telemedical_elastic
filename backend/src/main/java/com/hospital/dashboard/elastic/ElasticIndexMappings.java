package com.hospital.dashboard.elastic;

public class ElasticIndexMappings {

    public static final String DOCTORS_INDEX = "telemed_doctors";
    public static final String RECORDS_INDEX = "telemed_medical_records";
    public static final String FEEDBACK_INDEX = "telemed_feedbacks";
    public static final String HOSPITALS_INDEX = "telemed_hospitals";
    public static final String ANALYTICS_INDEX = "telemed_consultations_analytics";

    public static final String DOCTORS_MAPPING = """
    {
      "settings": {
        "number_of_shards": 1,
        "number_of_replicas": 0,
        "analysis": {
          "analyzer": {
            "doctor_analyzer": {
              "type": "custom",
              "tokenizer": "standard",
              "filter": ["lowercase", "asciifolding"]
            }
          }
        }
      },
      "mappings": {
        "properties": {
          "doctorId": { "type": "long" },
          "fullName": {
            "type": "text",
            "analyzer": "doctor_analyzer",
            "fields": { "keyword": { "type": "keyword" } }
          },
          "firstName": { "type": "keyword" },
          "lastName": { "type": "keyword" },
          "gender": { "type": "keyword" },
          "dob": { "type": "date", "format": "yyyy-MM-dd||epoch_millis" },
          "dateJoined": { "type": "date", "format": "yyyy-MM-dd||epoch_millis" },
          "licenseNo": { "type": "keyword" },
          "experience": { "type": "integer" },
          "specializations": { "type": "keyword" },
          "qualifications": { "type": "keyword" },
          "avgRating": { "type": "float" },
          "ratings": { "type": "integer" },
          "city": { "type": "keyword" },
          "area": { "type": "keyword" },
          "pincode": { "type": "keyword" },
          "location": { "type": "geo_point" },
          "hospitals": {
            "type": "nested",
            "properties": {
              "hospitalId": { "type": "long" },
              "hospitalName": { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
              "role": { "type": "keyword" },
              "city": { "type": "keyword" },
              "area": { "type": "keyword" }
            }
          },
          "phoneNumbers": { "type": "keyword" }
        }
      }
    }
    """;

    public static final String RECORDS_MAPPING = """
    {
      "settings": {
        "number_of_shards": 1,
        "number_of_replicas": 0,
        "analysis": {
          "analyzer": {
            "clinical_analyzer": {
              "type": "standard"
            }
          }
        }
      },
      "mappings": {
        "properties": {
          "medicalRecordId": { "type": "long" },
          "patientId": { "type": "long" },
          "patientName": { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
          "patientCity": { "type": "keyword" },
          "patientArea": { "type": "keyword" },
          "patientPincode": { "type": "keyword" },
          "patientLocation": { "type": "geo_point" },
          "symptoms": {
            "type": "text",
            "analyzer": "clinical_analyzer",
            "fields": { "raw": { "type": "keyword", "ignore_above": 256 } }
          },
          "diagnoses": {
            "type": "text",
            "analyzer": "clinical_analyzer",
            "fields": { "raw": { "type": "keyword", "ignore_above": 256 } }
          },
          "suggestedSpecialty": { "type": "keyword" },
          "createdAt": { "type": "date", "format": "yyyy-MM-dd||epoch_millis" }
        }
      }
    }
    """;

    public static final String FEEDBACK_MAPPING = """
    {
      "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
      "mappings": {
        "properties": {
          "feedbackId": { "type": "long" },
          "patientId": { "type": "long" },
          "patientName": { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
          "feedbackComment": { "type": "text" },
          "rating": { "type": "integer" },
          "sentiment": { "type": "keyword" },
          "doctorId": { "type": "long" },
          "doctorName": { "type": "keyword" },
          "specialization": { "type": "keyword" }
        }
      }
    }
    """;

    public static final String HOSPITALS_MAPPING = """
    {
      "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
      "mappings": {
        "properties": {
          "hospitalId": { "type": "long" },
          "name": { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
          "street": { "type": "text" },
          "area": { "type": "keyword" },
          "city": { "type": "keyword" },
          "pincode": { "type": "keyword" },
          "location": { "type": "geo_point" },
          "phones": { "type": "keyword" },
          "doctorCount": { "type": "integer" },
          "specialties": { "type": "keyword" }
        }
      }
    }
    """;

    public static final String ANALYTICS_MAPPING = """
    {
      "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
      "mappings": {
        "properties": {
          "consultationId": { "type": "long" },
          "patientId": { "type": "long" },
          "patientName": { "type": "keyword" },
          "patientCity": { "type": "keyword" },
          "doctorId": { "type": "long" },
          "doctorName": { "type": "keyword" },
          "doctorSpecialization": { "type": "keyword" },
          "date": { "type": "date", "format": "yyyy-MM-dd||epoch_millis" },
          "status": { "type": "keyword" },
          "consultationMode": { "type": "keyword" },
          "paymentAmount": { "type": "double" },
          "paymentMethod": { "type": "keyword" },
          "rating": { "type": "integer" }
        }
      }
    }
    """;
}
