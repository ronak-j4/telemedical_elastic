package com.hospital.dashboard.repository;

import com.hospital.dashboard.model.Doctor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class DoctorRepository {

    private final JdbcTemplate jdbcTemplate;

    public DoctorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Doctor> ROW_MAPPER = (rs, rowNum) -> {
        Doctor d = new Doctor();
        d.setDoctorId(rs.getLong("doctor_id"));
        d.setFirstName(rs.getString("first_name"));
        d.setLastName(rs.getString("last_name"));
        d.setDob(rs.getDate("dob") != null ? rs.getDate("dob").toLocalDate() : null);
        d.setGender(rs.getString("gender"));
        d.setDateJoined(rs.getDate("date_joined") != null ? rs.getDate("date_joined").toLocalDate() : null);
        d.setLicenseNumber(rs.getString("license_no"));
        d.setExperience(rs.getObject("experience") != null ? ((Number) rs.getObject("experience")).intValue() : null);
        return d;
    };

    public List<Doctor> findAll() {
        return jdbcTemplate.query("SELECT * FROM doctor ORDER BY doctor_id", ROW_MAPPER);
    }

    public Optional<Doctor> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM doctor WHERE doctor_id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    public Doctor save(Doctor d) {
        Long id = jdbcTemplate.queryForObject("SELECT seq_doctor.NEXTVAL FROM dual", Long.class);
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO doctor (doctor_id, first_name, last_name, dob, gender, date_joined, license_no, experience) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setLong(1, id);
            ps.setString(2, d.getFirstName());
            ps.setString(3, d.getLastName());
            ps.setDate(4, d.getDob() != null ? Date.valueOf(d.getDob()) : null);
            ps.setString(5, d.getGender());
            ps.setDate(6, d.getDateJoined() != null ? Date.valueOf(d.getDateJoined()) : null);
            ps.setString(7, d.getLicenseNumber());
            ps.setObject(8, d.getExperience());
            return ps;
        });
        d.setDoctorId(id);
        return d;
    }

    public int update(Long id, Doctor d) {
        return jdbcTemplate.update(
                "UPDATE doctor SET first_name = ?, last_name = ?, dob = ?, gender = ?, date_joined = ?, license_no = ?, experience = ? WHERE doctor_id = ?",
                d.getFirstName(), d.getLastName(),
                d.getDob() != null ? Date.valueOf(d.getDob()) : null,
                d.getGender(),
                d.getDateJoined() != null ? Date.valueOf(d.getDateJoined()) : null,
                d.getLicenseNumber(), d.getExperience(), id);
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM doctor WHERE doctor_id = ?", id);
    }
}
