package com.ssafy.checksource.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.google.gson.Gson;
import com.ssafy.checksource.config.security.JwtTokenProvider;
import com.ssafy.checksource.model.dto.AnalyProjectListDTO;
import com.ssafy.checksource.model.dto.GitLabConnectDTO;
import com.ssafy.checksource.model.dto.GitLabProjectDTO;
import com.ssafy.checksource.model.dto.GitLabProjectListDTO;
import com.ssafy.checksource.model.dto.PackageManageFileDTO;
import com.ssafy.checksource.model.dto.ProjectBranchesDTO;
import com.ssafy.checksource.model.dto.RepositoryTreeDTO;
import com.ssafy.checksource.model.dto.UserGitLabDTO;
import com.ssafy.checksource.model.dto.UserGitLabTokenDTO;
import com.ssafy.checksource.model.entity.Depart;
import com.ssafy.checksource.model.entity.GitLab;
import com.ssafy.checksource.model.entity.GitLabUser;
import com.ssafy.checksource.model.entity.Project;
import com.ssafy.checksource.model.entity.User;
import com.ssafy.checksource.model.key.GitLabUserKey;
import com.ssafy.checksource.model.repository.DepartRepository;
import com.ssafy.checksource.model.repository.GitLabRepository;
import com.ssafy.checksource.model.repository.GitLabUserRepository;
import com.ssafy.checksource.model.repository.OpensourceProjectRepository;
import com.ssafy.checksource.model.repository.ProjectRepository;
import com.ssafy.checksource.model.repository.UnmappedOpensourceRepository;
import com.ssafy.checksource.model.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class GitService {

	@Autowired
	RestTemplate restTemplate;
	private final JwtTokenProvider jwtTokenProvider;
	private final ModelMapper modelMapper = new ModelMapper();
	private final GitLabRepository gitLabRepository;
	private final UserRepository userRepository;
	private final ProjectRepository projectRepository;
	private final DepartRepository departRepository;
	private final GitLabUserRepository gitLabUserRepository;
	private final AnalyzeService analyzeService;
	private final UnmappedOpensourceRepository unmappedOpensourceRepository;
	private final OpensourceProjectRepository opensourceProjectRepository;

	// gitlab ?????? ?????? ??????
	public GitLabConnectDTO gitConnect(String username, String token, Long gitlabId) {
		GitLab gitlab = gitLabRepository.findById(gitlabId)
				.orElseThrow(() -> new IllegalArgumentException("no gitLab data"));
		String baseUrl = gitlab.getBaseUrl();
		String accessToken = gitlab.getRootAccessToken(); // ????????????

		String userId = jwtTokenProvider.getUserId(token);
		User user = userRepository.findByUserId(userId);

		String url = baseUrl + "users?username=";
		url += username;
		GitLabConnectDTO gitLabConnectDto = new GitLabConnectDTO();

		// ????????? ?????? ??????
		String s = null;
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("private-token", accessToken);
		HttpEntity entity = new HttpEntity(headers);

		try {
			// api ??????
			ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
			Gson gson = new Gson();
			UserGitLabDTO[] userGitLab = gson.fromJson(responseEntity.getBody(), UserGitLabDTO[].class);
			List<UserGitLabDTO> list = Arrays.asList(userGitLab);

			if (!list.isEmpty()) {
				UserGitLabDTO gitLabDto = list.get(0);
				// 1. user_access_token ??????
				// ???????????? ?????? api ??????
				String secondUrl = baseUrl + "users/" + gitLabDto.getId() + "/impersonation_tokens";
				JSONObject parameters = new JSONObject();
				parameters.put("name", "test");
				JSONArray array = new JSONArray();
				array.put("api");
				parameters.put("scopes", array);
				HttpEntity requestEntity = new HttpEntity(parameters.toString(), headers);
				ResponseEntity<String> response = restTemplate.exchange(secondUrl, HttpMethod.POST, requestEntity,
						String.class);
				UserGitLabTokenDTO userGitlabTokenDto = gson.fromJson(response.getBody(), UserGitLabTokenDTO.class);
				String userGitlabToken = userGitlabTokenDto.getToken();

				// 2. DB??? insert ??? true ??????
				GitLabUser gitlabUser = new GitLabUser();
				GitLabUserKey gitlabUserKey = new GitLabUserKey();
				gitlabUserKey.setGitlabId(gitlabId);
				gitlabUserKey.setUserId(userId);
				gitlabUser.setGitlabUserKey(gitlabUserKey);
				gitlabUser.setUserGitlabId(gitLabDto.getId());
				gitlabUser.setUsername(gitLabDto.getUsername());
				gitlabUser.setUserAccessToken(userGitlabToken);
				GitLabUser gitlabSaveUser = gitLabUserRepository.save(gitlabUser);
				// set
				gitLabConnectDto.setGitlabId(gitlabId);
				gitLabConnectDto.setUserGitlabId(gitlabSaveUser.getUserGitlabId());
				gitLabConnectDto.setUsername(gitlabSaveUser.getUsername());
				gitLabConnectDto.setFlag(true);
			} else {
				// username ???????????? ?????? ??????
				gitLabConnectDto.setFlag(false);
			}
			return gitLabConnectDto;
		} catch (HttpClientErrorException e) {
			// accessToken??? ???????????? ?????? ?????? -> root?????? ?????? ???????????? ?????? ????????? ????????????
			return gitLabConnectDto;
		} catch (JSONException e) {
			// json ?????? ??????
			e.printStackTrace();
			return gitLabConnectDto;
		}
	}

	// gitlab ?????? ?????? ??????
	public void deleteGitConnect(Long gitlabId, String token) {
		GitLabUser gitlabUser = new GitLabUser();
		GitLabUserKey gitlabUserKey = new GitLabUserKey();
		String userId = jwtTokenProvider.getUserId(token);
		gitLabRepository.findById(gitlabId).orElseThrow(() -> new IllegalArgumentException("no gitlab data"));
		gitlabUserKey.setGitlabId(gitlabId);
		gitlabUserKey.setUserId(userId);
		gitlabUser.setGitlabUserKey(gitlabUserKey);
		gitLabUserRepository.delete(gitlabUser);
	}

	// ???????????? ?????? ???????????? - ?????? ?????? ??????
	public GitLabProjectListDTO getProjects(String token, Long gitlabId) {
		// ??????
		GitLabProjectListDTO returnDto = new GitLabProjectListDTO();
		List<GitLabProjectDTO> gitLabProjectList = new ArrayList<GitLabProjectDTO>();

		String userId = jwtTokenProvider.getUserId(token);
		User user = userRepository.findByUserId(userId);
		GitLab gitlab = gitLabRepository.findById(gitlabId)
				.orElseThrow(() -> new IllegalArgumentException("no gitLab data"));
		GitLabUser gitlabUser = new GitLabUser();
		GitLabUserKey gitlabUserKey = new GitLabUserKey();

		gitlabUserKey.setGitlabId(gitlabId);
		gitlabUserKey.setUserId(userId);
		gitlabUser.setGitlabUserKey(gitlabUserKey);
		String userAccessToken = gitLabUserRepository.findByUserAndGitlab(user, gitlab).getUserAccessToken();

		String baseUrl = gitlab.getBaseUrl();
		String url = baseUrl + "/projects?membership=true" + "&per_page=50000";

		// ?????? ??????
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("private-token", userAccessToken);
		HttpEntity entity = new HttpEntity(headers);
		List<GitLabProjectDTO> gitLabProjectlist = new ArrayList<GitLabProjectDTO>();
		try {
			ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
			Gson gson = new Gson();
			GitLabProjectDTO[] gitLabProjectDto = gson.fromJson(responseEntity.getBody(), GitLabProjectDTO[].class);
			gitLabProjectlist = Arrays.asList(gitLabProjectDto);

			// ??????, ??????????????? ?????????
			for (GitLabProjectDTO gitLabProject : gitLabProjectlist) {
				String gitlabProjectId = gitLabProject.getId();
				Project project = projectRepository.findByGitProjectIdAndGitType(gitlabProjectId, gitlabId);
				if (project == null) {// ?????????
					gitLabProjectList.add(gitLabProject);
				} else {// ??????
					gitLabProject.setVerified(true);
					gitLabProjectList.add(gitLabProject);
				}
			}
			returnDto.setAccessflag(true);
			returnDto.setProjectList(gitLabProjectList);
			return returnDto;
		} catch (Exception e) {
			// ?????? ?????? ???????????? ?????? ?????? - ?????? ?????? ?????? ?????????
			returnDto.setAccessflag(false);
			returnDto.setProjectList(gitLabProjectList);
			return returnDto;
		}
	}

	// ???????????? ????????????
	public boolean deleteProject(String token, String gitlabProjectId, Long gitlabId) {
		String userId = jwtTokenProvider.getUserId(token);
		User user = userRepository.findByUserId(userId);
		Project project = projectRepository.findByGitProjectIdAndGitType(gitlabProjectId, gitlabId);
		if (user.getDepart().getDepartId() == project.getDepart().getDepartId()) {
			projectRepository.delete(project);
			return true;
		}
		return false;
	}

	// ???????????? ????????? ????????????
	public List<ProjectBranchesDTO> getBranches(String token, String gitlabProjectId, Long gitlabId) {
		GitLab gitlab = gitLabRepository.findById(gitlabId)
				.orElseThrow(() -> new IllegalArgumentException("no gitLab data"));
		String baseUrl = gitlab.getBaseUrl();
		String accessToken = gitlab.getRootAccessToken(); // ????????????
		String url = baseUrl + "/projects/" + gitlabProjectId + "/repository/branches";
		List<ProjectBranchesDTO> projectBranchesList = new ArrayList<ProjectBranchesDTO>();
		// ?????? ??????
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("private-token", accessToken);
		HttpEntity entity = new HttpEntity(headers);

		try {
			// api ??????
			ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
			Gson gson = new Gson();
			ProjectBranchesDTO[] branchList = gson.fromJson(responseEntity.getBody(), ProjectBranchesDTO[].class);
			projectBranchesList = Arrays.asList(branchList);

		} catch (HttpClientErrorException e) {
			// accessToken??? ???????????? ?????? ?????? -> root?????? ?????? ???????????? ?????? ????????? ????????????
			// ????????? ???????????? id ?????? ??????
			return projectBranchesList;
		}
		return projectBranchesList;
	}

	// ???????????? ???????????? - ??????, ?????????
	public boolean addProject(String token, List<GitLabProjectDTO> projectList, Long gitlabId) throws Exception {

		String userId = jwtTokenProvider.getUserId(token);
		User user = userRepository.findByUserId(userId);
		Depart depart = user.getDepart();

		// ????????? ???????????? ?????????
		List<AnalyProjectListDTO> analyProjectList = new ArrayList<AnalyProjectListDTO>();
		GitLab gitLab = gitLabRepository.findById(gitlabId)
				.orElseThrow(() -> new IllegalArgumentException("no gitlab Id in database"));
		String accessToken = gitLab.getRootAccessToken(); // ????????????
		String baseUrl = gitLab.getBaseUrl();

		// ??????????????? ??????
		for (GitLabProjectDTO gitLabProjectDTO : projectList) {
			AnalyProjectListDTO analyProjectListDto = new AnalyProjectListDTO();
			String gitlabProjectId = gitLabProjectDTO.getId();
			String projectName = gitLabProjectDTO.getName();
			String branch = gitLabProjectDTO.getBranch();
			String webUrl = gitLabProjectDTO.getWeb_url();

			// 1. repositoryTree ?????? ???????????? ????????????
			String url = baseUrl + "projects/" + gitlabProjectId + "/repository/tree?ref=" + branch
					+ "&recursive=true&per_page=50000";
			List<RepositoryTreeDTO> repositoryTreeList = new ArrayList<RepositoryTreeDTO>();
			RepositoryTreeDTO returnRepositoryDto = new RepositoryTreeDTO();

			// ?????? ??????
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_XML);
			headers.set("private-token", accessToken);
			HttpEntity entity = new HttpEntity(headers);
			try {// api - RepositoryTree ????????? ?????????
				ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity,
						String.class);
				Gson gson = new Gson();
				RepositoryTreeDTO[] repositoryTreeDto = gson.fromJson(responseEntity.getBody(),
						RepositoryTreeDTO[].class);
				repositoryTreeList = Arrays.asList(repositoryTreeDto);
			} catch (HttpClientErrorException e) {
				// ????????? ???????????? ?????? ?????? 401
				if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
					return false;
				}
				// repository tree??? ?????? ?????? 404 - nocontents
				if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
					// ????????? ????????? ???????????? ???????????? ??? ??????
					// ????????? ????????? ??????
					Project existProject = projectRepository.findByGitProjectIdAndGitType(gitlabProjectId, gitlabId);
					if (existProject != null) { // ????????? ????????? ??????????????? ????????? ?????????
						projectRepository.delete(existProject);
					}
					Project project = new Project();
					project.setUser(user);
					project.setDepart(depart);
					project.setName(gitLabProjectDTO.getName());
					project.setGitProjectId(gitlabProjectId); // gitlabProjectId
					project.setBranch(gitLabProjectDTO.getBranch());// ????????? ??????
					project.setGitType(gitlabId);// gitType
					project.setWebUrl(gitLabProjectDTO.getWeb_url());// webUrl
					project.setStatus(true);
					projectRepository.save(project);
					continue;
				}

			}

			// 2. repositoryTreeList?????? ????????? ????????? ?????? ????????? ??????
			List<RepositoryTreeDTO> packageManageFileList = new ArrayList<RepositoryTreeDTO>();
			for (RepositoryTreeDTO repositoryTree : repositoryTreeList) {
				String name = repositoryTree.getName();
				if (name.equals("pom.xml") || name.equals("package.json")) {
					returnRepositoryDto = repositoryTree;
					packageManageFileList.add(returnRepositoryDto);
				}
			}

			// set
			analyProjectListDto.setGitProjectId(gitlabProjectId);
			analyProjectListDto.setProjectName(projectName);
			analyProjectListDto.setBranch(branch);
			analyProjectListDto.setWebUrl(webUrl);
			analyProjectListDto.setPackageManageFileList(packageManageFileList); // ???????????????????????? ?????????
			analyProjectList.add(analyProjectListDto);
		} // end project for???

		//System.out.println(analyProjectList);

		// 3. ????????? ??????????????? ?????????????????? ?????? ??????????????? ????????? ????????? ????????? contents ??????
		for (AnalyProjectListDTO analyProjectListDto : analyProjectList) {
			List<RepositoryTreeDTO> packageManageFileList = analyProjectListDto.getPackageManageFileList();
			String gitlabProjectId = analyProjectListDto.getGitProjectId();

			// ????????? ??????
			Project existProject = projectRepository.findByGitProjectIdAndGitType(gitlabProjectId, gitlabId);
			if (existProject != null) { // ????????? ????????? ??????????????? ?????????
				projectRepository.delete(existProject);
			}

			Project project = new Project();
			project.setUser(user);
			project.setDepart(depart);
			project.setName(analyProjectListDto.getProjectName());
			project.setGitProjectId(gitlabProjectId); // gitlabProjectId
			project.setBranch(analyProjectListDto.getBranch());// ????????? ??????
			project.setGitType(gitlabId);// gitType
			project.setWebUrl(analyProjectListDto.getWebUrl());// webUrl
			project.setStatus(true);
			Project newProject = projectRepository.save(project);
			Long projectId = newProject.getProjectId();

			// ??????????????? ??????????????? ?????????
			for (RepositoryTreeDTO packageManageFile : packageManageFileList) {
				String path = packageManageFile.getPath();

				// path ?????? url encoding?????? api ??????
				URI uri = UriComponentsBuilder
						.fromUriString(baseUrl + "projects/{projectid}/repository/files/{urlEncoding}?ref="+analyProjectListDto.getBranch())
						.encode().buildAndExpand(gitlabProjectId, path).toUri();
				// ??????
				HttpHeaders headers = new HttpHeaders();
				headers.setContentType(MediaType.APPLICATION_XML);
				headers.set("private-token", accessToken);
				HttpEntity entity = new HttpEntity(headers);
				try {
					ResponseEntity<String> responseEntity = restTemplate.exchange(uri, HttpMethod.GET, entity,
							String.class);
					Gson gson = new Gson();
					PackageManageFileDTO packageManageFileDto = gson.fromJson(responseEntity.getBody(),
							PackageManageFileDTO.class);
					// contents ??????
					String contents = packageManageFileDto.getContent();
					String filePath = packageManageFileDto.getFile_path();
					String fileName = packageManageFileDto.getFile_name();
					// 4. base64 - decoding ??? ?????? ??????
					analyzeService.analyze(projectId, fileName, contents, filePath); // ??????

				} catch (HttpClientErrorException e) {
					System.out.println(e.getStatusCode());
					// ????????? ???????????? ?????? ?????? 401
					if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
						return false;
					}
				}
			}
		}

		return true;
	}
}
