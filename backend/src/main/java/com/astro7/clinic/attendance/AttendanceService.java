package com.astro7.clinic.attendance;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import com.astro7.clinic.cpf.Cpf;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {

	private static final Pattern REPEATED_WHITESPACE = Pattern.compile("\\s+");

	private final AttendanceRepository repository;
	private final Clock clock;

	public AttendanceService(AttendanceRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/**
	 * A consulta antes de gravar cobre o caso comum. A garantia é o índice único
	 * {@code uq_attendance_open_cpf}: duas requisições simultâneas passam juntas pela
	 * consulta, e só uma passa pelo índice.
	 */
	public Attendance open(String patientName, String cpf) {
		String cpfDigits = Cpf.digitsOf(cpf);
		if (repository.existsByCpfAndStatusIn(cpfDigits, AttendanceStatus.OPEN)) {
			throw new OpenAttendanceExistsException();
		}
		Attendance attendance = Attendance.open(normalizeName(patientName), cpfDigits, LocalDateTime.now(clock));
		try {
			return repository.saveAndFlush(attendance);
		}
		catch (DataIntegrityViolationException ex) {
			throw new OpenAttendanceExistsException(ex);
		}
	}

	/**
	 * Abre um novo atendimento para o paciente de um atendimento já encerrado.
	 * O CPF vem do banco: o front só conhece a versão mascarada.
	 */
	public Attendance renew(long previousId) {
		Attendance previous = findById(previousId);
		if (AttendanceStatus.OPEN.contains(previous.getStatus())) {
			throw new OpenAttendanceExistsException();
		}
		return open(previous.getPatientName(), previous.getCpf());
	}

	@Transactional(readOnly = true)
	public Attendance findById(long id) {
		return repository.findById(id).orElseThrow(() -> new AttendanceNotFoundException(id));
	}

	private static String normalizeName(String patientName) {
		return REPEATED_WHITESPACE.matcher(patientName.strip()).replaceAll(" ");
	}

}
