package com.br.pontu.services;

import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.br.pontu.config.DAO;
import com.br.pontu.entity.DiaComHoras;
import com.br.pontu.entity.PontoData;
import com.br.pontu.entity.PontoHora;
import com.br.pontu.entity.User;
import com.br.pontu.repositories.PontoDataRepository;
import com.br.pontu.repositories.PontoHoraRepository;
import com.br.pontu.repositories.UserRepository;

/**
 *
 * @author Alves
 */
@Service("pontoDataHoraService")
public class PontoDataHoraServiceImpl implements PontoDataHoraService {

	// Injeções --------------------------------------------------------
	@Autowired
	private PontoHoraRepository pontoHoraRepository;
	@Autowired
	private PontoDataRepository pontoDataRepository;
	@Autowired
	private UserServiceImpl userService;
	@Autowired
	private DAO dao;
	@Autowired
	private UserRepository userRepository;

	// Função resposável por bater ponto, garantir unicidade e conscistência do
	// banco.
	@Override
	public boolean baterPonto(String matricula, String password) {

		User user = userRepository.findByMatricula(matricula);
		
		//Condição para caso não tenha encontrado nada no banco
		if (user == null || matricula.isEmpty() || password.isEmpty()) {
			
			return false;
		}
		
		

		if (verificarUserESenha(matricula, password, user)) {

			// Pega a data e formata
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
			Calendar dia = null;
			dia = Calendar.getInstance();
			String diaFormatado = dateFormat.format(dia.getTime());

			// Pega as horas e formata
			DateFormat hourFormat = new SimpleDateFormat("HH:mm");
			Calendar hour = null;
			hour = Calendar.getInstance();
			String horaFormatado = hourFormat.format(hour.getTime());

			// Cria Instancias de PontoData e PontoHora
			PontoData pdata = new PontoData();
			PontoHora phora = new PontoHora();

			// Garante apenas uma data por usuário
			Long dia_id = verificarDiaExistente(diaFormatado, user.getId());

			//
			if (dia_id != null) {

				if (!verificarPontosRepetidos(dia_id, horaFormatado)) {

					// Salva a hora, e o ID do dia correspondente
					phora.setHora(horaFormatado);
					phora.setDataId(dia_id);
					pontoHoraRepository.save(phora);
					
					pdata = pontoDataRepository.findOne(dia_id);
					pdata.getHoras().add(phora);
					pdata = pontoDataRepository.save(pdata);

				}

				return true;

			} else {

				// Salva o dia com a data, e o ID do usuário
				pdata.setDia(diaFormatado);
				pdata.setUserId(user.getId());
				pdata = pontoDataRepository.save(pdata);
				
				// Vincula a data ao usuário
				user.getPonto().add(pdata);
				userRepository.save(user);
				
				// Salva a hora, e o ID do dia correspondente
				phora.setHora(horaFormatado);
				phora.setDataId(pdata.getId());
				phora = pontoHoraRepository.save(phora);

				// Vincula a hora a data
				List<PontoHora> hora = new ArrayList<>(); //Como será o primeiro horário desse dia, criar arraylist para não ocorrer null pointer.
				hora.add(phora);
				pdata.setHoras(hora);
				pontoDataRepository.save(pdata);

				return true;

			}
		}

		return false;
	}

	// Função que verifica se já existe aquela data salva no banco
	// caso exista = Retorna ID do dia
	// caso contrário = Retorna null
	private Long verificarDiaExistente(String diaFormatado, Long userId) {

		List<PontoData> datas = pontoDataRepository.findByUserId(userId, diaFormatado);

		for (int i = 0; i < datas.size(); i++) {

			if (diaFormatado.equals(datas.get(i).getDia()) && datas.get(i).getUserId().equals(userId)) {

				return datas.get(i).getId();
			}
		}

		return null;
	}

	// Função que checa se o Usuário buscado do banco, tenha a matricula e a senha
	// iguais as do banco.
	private Boolean verificarUserESenha(String matricula, String password, User user) {

		try {

			// Encripta se a senha recebida para comparar com a do banco
			String passwordEncode = userService.encodePassword(password);

			// Checa se ambos tanto a matricula, quanto o password é o mesmo do banco	
			if (matricula.equals(user.getMatricula()) && passwordEncode.equals(user.getPassword())) {

				return true;
			}

			return false;

		} catch (NoSuchAlgorithmException e) {

			e.printStackTrace();
		}

		return false;
	}

	// Return true: se houver pontos repetidos
	// Return false: não houver pontos repetidos
	private Boolean verificarPontosRepetidos(Long dia_id, String horaFormatado) {

		List<String> horarios = pontoHoraRepository.findByDayId(dia_id);

		// Variável que permite desconsiderar pontos batidos nesse intervalo de tempo
		int temporizador = 3;

		int horBanco = 0, horFormat = 0;
		int minBanco = 0, minFormat = 0;

		// Formata horas e min atuais
		horFormat = Integer.parseInt(horaFormatado.substring(0, 2));
		minFormat = Integer.parseInt(horaFormatado.substring(3));
		// Converte tudo para minutos
		minFormat = minFormat + (horFormat * 60);

		for (int i = 0; i < horarios.size(); i++) {

			// Formata horas e min vindos do banco
			minBanco = Integer.parseInt(horarios.get(i).substring(3));
			horBanco = Integer.parseInt(horarios.get(i).substring(0, 2));
			minBanco = minBanco + (horBanco * 60);

			// Faz uma subtração e checa com o temporizador
			if ((minFormat - minBanco) <= temporizador) {

				return true;
			}
		}

		return false;
	}

	// Função que retorna os dias com horários de um determinado usuário
	@Override
	public List<DiaComHoras> buscar30Dias(Long userId) {

		String dataAnterior = null, dataAtual = null;
		int qDias = 30;

		// Pega a data e formata
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
		Calendar dia = null;
		dia = Calendar.getInstance();
		dataAtual = dateFormat.format(dia.getTime());

		// Pega a data e subtrai 30 dias
		dia = Calendar.getInstance();
		dia.add(Calendar.DATE, - qDias);
		dataAnterior = dateFormat.format(dia.getTime());

		//Query para select ao banco
		String sql = "SELECT dia, hora FROM ponto_data INNER JOIN ponto_hora ON ponto_data.id = ponto_hora.data_id "
				+ "WHERE ponto_data.user_id = '" + userId + "' AND (ponto_data.dia BETWEEN '" + dataAnterior + "' AND '"
				+ dataAtual + "');";

		try {
			
			//Instacia lista da Classe utilitária DiaComHoras
			List<DiaComHoras> listaDiasComHoras = new ArrayList<>();
			
			//Cria conexão com o SBGD e realiza a consulta
			Connection conn = dao.getConnection();
			java.sql.PreparedStatement p = conn.prepareStatement(sql);
			ResultSet rs = p.executeQuery();

			//Loop para extrair todas as tuplas
			while (rs.next()) {

				DiaComHoras aux = new DiaComHoras();
				
				aux.setDia(rs.getString("dia"));
				aux.setHora(rs.getString("hora"));

				listaDiasComHoras.add(aux);	
			}

			return listaDiasComHoras;
			
		} catch (Exception ex) {
			
			ex.printStackTrace();
		}
		
		//Por defult caso encotre algum erro 
		return null;
	}

	// =======================================================================================
	// Métodos posteriores para serem implementados
	@Override
	public User editarPonto(User user) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void deletarPonto(User user) {
		// TODO Auto-generated method stub

	}

	@Override
	public void buscar60Dias() {
		// TODO Auto-generated method stub

	}

	@Override
	public void buscar90Dias() {
		// TODO Auto-generated method stub

	}

}
