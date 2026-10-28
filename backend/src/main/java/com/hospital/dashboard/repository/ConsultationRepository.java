package com.hospital.dashboard.repository;

import com.hospital.dashboard.model.Consultation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class ConsultationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConsultationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String SELECT_ALL =
            "SELECT consultation_id, patient_id, doctor_id, booking_date, \"Date\", start_time, end_time, " +
            "status, consultation_mode, video_link, call_number, chat_transcript_id FROM consultation";

    private static final RowMapper<Consultation> ROW_MAPPER = (rs, rowNum) -> {
        Consultation c = new Consultation();
        c.setConsultationId(rs.getLong("consultation_id"));
        c.setPatientId(rs.getLong("patient_id"));
        c.setDoctorId(rs.getLong("doctor_id"));
        c.setBookingDate(rs.getDate("booking_date") != null ? rs.getDate("booking_date").toLocalDate() : null);
        c.setDate(rs.getDate("Date") != null ? rs.getDate("Date").toLocalDate() : null);
        c.setStartTime(rs.getString("start_time"));
        c.setEndTime(rs.getString("end_time"));
        c.setStatus(rs.getString("status"));
        c.setConsultationMode(rs.getString("consultation_mode"));
        c.setVideoLink(rs.getString("video_link"));
        c.setCallNumber(rs.getString("call_number"));
        c.setChatTranscriptId(rs.getString("chat_transcript_id"));
        return c;
    };

    public List<Consultation> findAll() {
        return jdbcTemplate.query(SELECT_ALL + " ORDER BY consultation_id", ROW_MAPPER);
    }

    public Optional<Consultation> findById(Long id) {
        return jdbcTemplate.query(SELECT_ALL + " WHERE consultation_id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    public Consultation save(Consultation c) {
        Long id = jdbcTemplate.queryForObject("SELECT seq_consultation.NEXTVAL FROM dual", Long.class);
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO consultation (consultation_id, patient_id, doctor_id, booking_date, \"Date\", start_time, end_time, status, consultation_mode, video_link, call_number, chat_transcript_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setLong(1, id);
            ps.setLong(2, c.getPatientId());
            ps.setLong(3, c.getDoctorId());
            ps.setDate(4, c.getBookingDate() != null ? Date.valueOf(c.getBookingDate()) : null);
            ps.setDate(5, c.getDate() != null ? Date.valueOf(c.getDate()) : null);
            ps.setString(6, c.getStartTime());
            ps.setString(7, c.getEndTime());
            ps.setString(8, c.getStatus());
            ps.setString(9, c.getConsultationMode());
            ps.setString(10, c.getVideoLink());
            ps.setString(11, c.getCallNumber());
            if (c.getChatTranscriptId() != null) ps.setString(12, c.getChatTranscriptId());
            else ps.setNull(12, Types.VARCHAR);
            return ps;
        });
        c.setConsultationId(id);
        return c;
    }

    public int update(Long id, Consultation c) {
        return jdbcTemplate.update(
                "UPDATE consultation SET patient_id = ?, doctor_id = ?, booking_date = ?, \"Date\" = ?, start_time = ?, end_time = ?, status = ?, consultation_mode = ?, video_link = ?, call_number = ?, chat_transcript_id = ? WHERE consultation_id = ?",
                c.getPatientId(), c.getDoctorId(),
                c.getBookingDate() != null ? Date.valueOf(c.getBookingDate()) : null,
                c.getDate() != null ? Date.valueOf(c.getDate()) : null,
                c.getStartTime(), c.getEndTime(), c.getStatus(), c.getConsultationMode(),
                c.getVideoLink(), c.getCallNumber(), c.getChatTranscriptId(), id);
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM consultation WHERE consultation_id = ?", id);
    }
}
