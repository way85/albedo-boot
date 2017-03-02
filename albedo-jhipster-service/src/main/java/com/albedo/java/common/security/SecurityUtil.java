package com.albedo.java.common.security;

import com.albedo.java.common.config.AlbedoProperties;
import com.albedo.java.common.domain.data.DynamicSpecifications;
import com.albedo.java.common.domain.data.SpecificationDetail;
import com.albedo.java.common.repository.data.JpaCustomeRepository;
import com.albedo.java.modules.sys.domain.*;
import com.albedo.java.modules.sys.repository.*;
import com.albedo.java.util.*;
import com.albedo.java.util.domain.Globals;
import com.albedo.java.util.domain.QueryCondition;
import com.albedo.java.util.spring.SpringContextHolder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Utility class for Spring Security.
 */
@SuppressWarnings("unchecked")
public final class SecurityUtil {

	protected static Logger logger = LoggerFactory.getLogger(SecurityUtil.class);

	public static UserRepository userRepository = SpringContextHolder.getBean(UserRepository.class);
	public static AreaRepository areaRepository = SpringContextHolder.getBean(AreaRepository.class);

	public static RoleRepository roleRepository = SpringContextHolder.getBean(RoleRepository.class);
	public static OrgRepository orgRepository = SpringContextHolder.getBean(OrgRepository.class);
	public static JpaCustomeRepository<?> baseRepository = SpringContextHolder.getBean(JpaCustomeRepository.class);

	public static ModuleRepository moduleRepository = SpringContextHolder.getBean(ModuleRepository.class);
	
	
	
	
	public static AlbedoProperties albedoProperties = SpringContextHolder.getBean(AlbedoProperties.class);

	public static final String USER_CACHE = "-userCache-";
	public static final String USER_CACHE_ID_ = "id_";
	public static final String USER_CACHE_LOGIN_NAME_ = "ln";

	/*** 当前用户拥有 组织集合 */
	public static final String CACHE_ORG_LIST = "cachaOrgList";
	/*** 当前用户拥有 区域集合 */
	public static final String CACHE_AREA_LIST = "cachaAreaList";
	/*** 当前用户拥有 模块集合 */
	public static final String CACHE_MODULE_LIST = "cachaModuleList";
	/*** 当前用户拥有 角色集合 */
	public static final String CACHE_ROLE_LIST = "cachaRoleList";
	public static final String STAFF_PRINCIPAL = "principal";
	
	private static Map<String, Object> dataMap = Maps.newHashMap();

	private SecurityUtil() {
	}

	public static boolean isAdmin(String id) {
		return "1".equals(id);
	}
	public static boolean isAdmin() {
		return isAdmin(getCurrentUserId());
	}
	/**
	 * Get the login of the current user.
	 *
	 * @return the login of the current user
	 */
	public static String getCurrentUserId() {
		SecurityContext securityContext = SecurityContextHolder.getContext();
		Authentication authentication = securityContext.getAuthentication();
		String userName = null;
		if (authentication != null) {
			if (authentication.getPrincipal() instanceof UserPrincipal) {
				UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
				userName = userPrincipal.getUserId();
			} else if (authentication.getPrincipal() instanceof UserDetails) {
				UserDetails springSecurityUser = (UserDetails) authentication.getPrincipal();

				userName = springSecurityUser.getUsername();
			} else if (authentication.getPrincipal() instanceof String) {
				userName = (String) authentication.getPrincipal();
			}
		}
		return userName;
	}

	public static String getCurrentAuditor() {
		String userName = SecurityUtil.getCurrentUserId();
		return (userName != null ? userName : Globals.SYSTEM_ACCOUNT);
	}

	/**
	 * 根据ID获取用户
	 * 
	 * @param id
	 * @return 取不到返回null
	 */
	public static User getByUserId(String userId) {
		User user = CacheUtil.getJson(USER_CACHE, USER_CACHE_ID_ + userId, User.class);
		if (user == null) {
			user = (User) baseRepository.findEntityByHQL("from User where id=:p1", userId);
			if (user == null)
				throw new UsernameNotFoundException("User " + userId + " was not found in the database");
			String json = Json.toJsonString(user);
			CacheUtil.put(USER_CACHE, USER_CACHE_ID_ + user.getId(), json);
			CacheUtil.put(USER_CACHE, USER_CACHE_LOGIN_NAME_ + user.getLoginId(), json);
		}
		return user;
	}

	/**
	 * 根据LoginId获取用户
	 * 
	 * @param id
	 * @return 取不到返回null
	 */
	public static User getByLoginId(String loginId) {
		User user = (User) CacheUtil.getJson(USER_CACHE, USER_CACHE_LOGIN_NAME_ + loginId, User.class);
		if (user == null) {
			user = (User) baseRepository.findEntityByHQL("from User where loginId=:p1", loginId);
			if (user != null) {
				String json = Json.toJsonString(user);
				CacheUtil.put(USER_CACHE, USER_CACHE_ID_ + user.getId(), json);
				CacheUtil.put(USER_CACHE, USER_CACHE_LOGIN_NAME_ + user.getLoginId(), json);
			}
		}
		return user;
	}

	public static User getCurrentUser() {
		User user = getByUserId(getCurrentUserId());
		if (user == null)
			user = new User();
		return user;
	}

	/**
	 * 返回当前用户可操作状态不为已删除的所有模块
	 * 
	 * @param refresh
	 *            是否从数据库中更新
	 * @return
	 */
	public static List<Module> getModuleList() {
		return getModuleList(false, null);
	}

	public static List<Module> getModuleList(String userId) {
		return getModuleList(false, userId);
	}

	/**
	 * 返回当前用户可操作状态不为已删除的所有模块
	 * 
	 * @param refresh
	 *            是否从数据库中更新
	 * @return
	 */
	public static List<Module> getModuleList(boolean refresh, String userId) {
		if (PublicUtil.isEmpty(userId))
			userId = getCurrentUserId();
		List<Module> moduleList = getCacheJsonArray(CACHE_MODULE_LIST, userId, Module.class);
		if (PublicUtil.isEmpty(moduleList) || refresh) {
			moduleList = isAdmin(userId) ? moduleRepository.findAllAuthenticationList(Module.FLAG_NORMAL)
					: baseRepository.findListByHQL("select distinct m from Module m, Role r, User u where m in elements (r.modules) and r in elements (u.roles) and m.status=0 and r.status=0 and u.status=0 and u.id=:p1 order by m.sort", userId);
			putCache(CACHE_MODULE_LIST, Json.toJsonString(moduleList), userId);
		}
		return moduleList;
	}

	public static List<Area> getAreaList() {
		List<Area> areaList = getCacheJsonArray(CACHE_AREA_LIST, Area.class);
		if (PublicUtil.isEmpty(areaList)) {
			SpecificationDetail<Area> spd = DynamicSpecifications
					.bySearchQueryCondition(QueryCondition.ne(Org.F_STATUS, Org.FLAG_DELETE));
			spd.orderASC(Area.F_ID);
			areaList = areaRepository.findAll(spd);
			putCache(CACHE_AREA_LIST, Json.toJsonString(areaList));
		}
		return areaList;
	}

	public static List<Org> getOrgList() {
		String userId = getCurrentUserId();
		List<Org> orgList = getCacheJsonArray(CACHE_ORG_LIST, Org.class);
		if (PublicUtil.isEmpty(orgList)) {
			SpecificationDetail<Org> spd = new SpecificationDetail<Org>()
					.and(QueryCondition.ne(Org.F_STATUS, Org.FLAG_DELETE));
			if (!SecurityUtil.isAdmin(userId)) {
				spd.orAll(dataScopeFilter(getCurrentUserId(), "this", ""));
			}
			spd.orderASC(Org.F_SORT);
			orgList = orgRepository.findAll(spd);
			putCache(CACHE_ORG_LIST, Json.toJsonString(orgList));
		}
		return orgList;
	}
	
	public static List<Role> getRoleList() {
		String userId = getCurrentUserId();
		List<Role> roleList = getCacheJsonArray(CACHE_ROLE_LIST, Role.class);
		if (PublicUtil.isEmpty(roleList)) {
			SpecificationDetail<Role> spd = new SpecificationDetail<Role>()
					.and(QueryCondition.eq(Role.F_STATUS, Role.FLAG_NORMAL));
			if (!SecurityUtil.isAdmin(userId)) {
				spd.orAll(dataScopeFilter(getCurrentUserId(), "org", "creator"));
			}
			spd.orderASC(Role.F_SORT);
			roleList = roleRepository.findAll(spd);
			putCache(CACHE_ROLE_LIST, Json.toJsonString(roleList));
		}
		return roleList;
	}
	
	/**
	 * Check if a user is authenticated.
	 *
	 * @return true if the user is authenticated, false otherwise
	 */
	public static boolean isAuthenticated() {
		SecurityContext securityContext = SecurityContextHolder.getContext();
		Authentication authentication = securityContext.getAuthentication();
		if (authentication != null) {
			Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
			if (authorities != null) {
				for (GrantedAuthority authority : authorities) {
					if (authority.getAuthority().equals(AuthoritiesConstants.ANONYMOUS)) {
						return false;
					}
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * If the current user has a specific authority (security role).
	 *
	 * <p>
	 * The name of this method comes from the isUserInRole() method in the
	 * Servlet API
	 * </p>
	 *
	 * @param authority
	 *            the authority to check
	 * @return true if the current user has the authority, false otherwise
	 */
	public static boolean isCurrentUserInRole(String authority) {
		SecurityContext securityContext = SecurityContextHolder.getContext();
		Authentication authentication = securityContext.getAuthentication();
		if (authentication != null) {
			if (authentication.getPrincipal() instanceof UserDetails) {
				UserDetails springSecurityUser = (UserDetails) authentication.getPrincipal();
				return springSecurityUser.getAuthorities().contains(new SimpleGrantedAuthority(authority));
			}
		}
		return false;
	}

	private static String getUserKey(String key, String userId) {
		return PublicUtil.toAppendStr(USER_CACHE, "_", key, "_", userId);
	}

	public static <T> List<T> getCacheJsonArray(String key, Class<T> clazz) {
		try {
			String value = PublicUtil.toStrString(getCacheDefult(key, null, null));
			return Json.parseArray(value, clazz);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("getCacheJsonArray msg {}", e.getMessage());
			return null;
		}
		
	}
	
	public static <T> List<T> getCacheJsonArray(String key, String userId, Class<T> clazz) {
		try {
			String value = PublicUtil.toStrString(getCacheDefult(key, null, userId));
			return Json.parseArray(value, clazz);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("getCacheJsonArray userId msg {}", e.getMessage());
			return null;
		}
	}
	
	public static Object getCache(String key) {
		return getCacheDefult(key, null, null);
	}

	public static Object getCache(String key, String userId) {
		return getCacheDefult(key, null, userId);
	}
	
	public static  <T> T getCacheJson(String key, String userId, Class<T> clazz) {
		try {
			String value = PublicUtil.toStrString(getCacheDefult(key, null, userId));
			return Json.parseObject(value, clazz);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("getCacheJson userId msg {}", e.getMessage());
			return null;
		}
		
	}
	

	public static Object getCacheDefult(String key, Object defaultValue, String userId) {
		Date date = new Date();
		Object obj = null;
		if (PublicUtil.isEmpty(userId))
			userId = getCurrentUserId();
		if (PublicUtil.isEmpty(userId)) {
			logger.error("login user is null, get userCache failed");
		} else {
			String realKey = getUserKey(key, userId);
			if(albedoProperties.getCluster()){
				obj = JedisUtil.getUser(realKey);
			}else{
				obj = dataMap.get(realKey);
				if(obj == null){
					obj = JedisUtil.getUser(realKey);
					dataMap.put(realKey, obj);
				}
			}
			
		}
		logger.warn("getCacheDefult {} ms", new Date().getTime() - date.getTime());
		return obj == null ? defaultValue : obj;
	}

	public static void putCache(String key, Object value) {
		putCache(key, value, null);
	}

	public static void putCache(String key, Object value, String userId) {
		if (PublicUtil.isEmpty(userId))
			userId = getCurrentUserId();
		if (PublicUtil.isEmpty(userId)) {
			logger.error("login user is null, put userCache failed");
		} else {
			// JedisUtil.mapObjectPut(USER_CACHE, PublicUtil.toAppendStr("_",
			// key, "_", userId), value);
			String realKey = getUserKey(key, userId);
			if(!albedoProperties.getCluster()){
				dataMap.put(realKey, value);
			}
				JedisUtil.putUser(realKey, value);
		}
	}

	public static void removeAllCache(String key) {
		removeCache(key, null);
	}

	public static void removeCurrentCache(String key) {
		removeCache(key, getCurrentUserId());
	}

	public static void removeCache(String key, String userId) {
		
		String realKey = getUserKey(key, userId);
		JedisUtil.removeUser(realKey);
		if(!albedoProperties.getCluster()){
			dataMap.remove(realKey);
		}
		
		// Map<String, Object> map = (Map<String, Object>)
		// JedisUtil.getObject();
		// Iterator<String> keys = map.keySet().iterator();
		// String temp = null;
		// while (keys.hasNext()) {
		// temp = keys.next();
		// if (temp.contains(PublicUtil.toAppendStr("_", key, "_", userId))) {
		// JedisUtil.mapObjectRemove(USER_CACHE, temp);
		// }
		// }
	}

	/**
	 * 清除所有数据jedis用户缓存
	 */
	public static void clearUserJedisCache() {
		JedisUtil.clearAllCacheUser();
		dataMap.clear();
	}

	/**
	 * 清除所有数据本地用户缓存
	 */
	public static void clearUserLocalCache() {
		CacheUtil.removeCache(USER_CACHE);
	}

	public static boolean hasPermission(String permission) {
		List<Module> list = getModuleList();
		if (isAdmin(getCurrentUserId()))
			return true;
		if (PublicUtil.isEmpty(permission))
			return false;
		permission = PublicUtil.toAppendStr(",", permission,",");
		for (Module module : list) {
			if (permission.contains(PublicUtil.toAppendStr(",", module.getPermission(),","))) {
				return true;
			}
		}

		return false;
	}

	/**
	 * 当前用户 数据范围过滤 机构表别名 org 用户表别名 creator
	 * 
	 * @return 标准连接条件对象
	 */
	public static List<QueryCondition> dataScopeFilter() {
		return dataScopeFilter(getCurrentUserId(), "creator.org", "creator");
	}

	/**
	 * 数据范围过滤
	 * 
	 * @param staff
	 *            当前用户对象，通过“entity.getCurrentUser()”获取
	 * @param orgAlias
	 *            机构表别名，多个用“,”逗号隔开。
	 * @param userAlias
	 *            用户表别名，多个用“,”逗号隔开，传递空，忽略此参数
	 * @return 标准连接条件对象
	 */
	public static List<QueryCondition> dataScopeFilter(String userId, String orgAlias, String userAlias) {
		// 进行权限过滤，多个角色权限范围之间为或者关系。
		List<String> dataScope = Lists.newArrayList();
		List<QueryCondition> queryConditions = Lists.newArrayList();
		// 超级管理员，跳过权限过滤
		if (!SecurityUtil.isAdmin(userId)) {
			User user = getByUserId(userId);
			boolean isDataScopeAll = false;
			String tempOrgId = null;
			for (Role r : user.getRoles()) {
				for (String oa : StringUtil.splitDefault(orgAlias)) {
					if (!dataScope.contains(r.getDataScope()) && StringUtil.isNotBlank(oa)) {
						tempOrgId = PublicUtil.toAppendStr(oa, ".id");
						if (Role.DATA_SCOPE_ALL.equals(r.getDataScope())) {
							isDataScopeAll = true;
						} else if (Role.DATA_SCOPE_ORG_AND_CHILD.equals(r.getDataScope())) {
							queryConditions.add(QueryCondition.eq(tempOrgId, user.getOrgId()));
							queryConditions.add(QueryCondition.like(PublicUtil.toAppendStr(oa, ".parentIds"),
									PublicUtil.toAppendStr(user.getOrg().getParentIds(), user.getOrgId(), ",%'")));
						} else if (Role.DATA_SCOPE_ORG.equals(r.getDataScope())) {
							queryConditions.add(QueryCondition.eq(tempOrgId, user.getOrgId()));
							queryConditions
									.add(QueryCondition.eq(PublicUtil.toAppendStr(oa, ".parentId"), user.getOrgId()));
						} else if (Role.DATA_SCOPE_SELF.equals(r.getDataScope())
								|| Role.DATA_SCOPE_CUSTOM.equals(r.getDataScope())) {
							if (PublicUtil.isNotEmpty(r.getOrgIds())) {
								queryConditions.add(QueryCondition.in(tempOrgId,
										Lists.newArrayList(StringUtil.splitDefault(r.getOrgIds()))));
							}
						}
						dataScope.add(String.valueOf(r.getDataScope()));
					}
				}
			}
			// 如果没有全部数据权限，并设置了用户别名，则当前权限为本人；如果未设置别名，当前无权限为已植入权限
			if (!isDataScopeAll) {
				if (StringUtil.isNotBlank(userAlias)) {
					for (String ua : StringUtil.splitDefault(userAlias)) {
						queryConditions.add(QueryCondition.eq(PublicUtil.toAppendStr(ua, ".id"), user.getId()));
					}
				} else {
					for (String oa : StringUtil.splitDefault(orgAlias)) {
						queryConditions.add(QueryCondition.isNull(PublicUtil.toAppendStr(oa, ".id")));
					}
				}
			} else {
				// 如果包含全部权限，则去掉之前添加的所有条件，并跳出循环。
				queryConditions.clear();
			}
		}
		return queryConditions;
	}

	/**
	 * 签名算法
	 * 
	 * @param data
	 * @return
	 */
	public static String sign(Map<String, String> data, String key) {
		String resp = "";
		StringBuffer unsignString = new StringBuffer();
		data.put("sig", key);
		List<String> nameList = new ArrayList<String>(data.keySet());
		// 首先按字段名的字典序排列
		Collections.sort(nameList);
		for (String name : nameList) {
			String value = data.get(name);
			if (value != null) {
				unsignString.append(name).append("=").append(value).append("&");
			}
		}
		try {
			if (unsignString.length() > 0)
				unsignString.delete(unsignString.length() - 1, unsignString.length());
			resp = md5(unsignString.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
		resp = resp.toLowerCase();
		return resp;
	}

	public static String md5(String value) throws Exception {
		MessageDigest mdInst = MessageDigest.getInstance("MD5");
		mdInst.update(value.getBytes("UTF-8"));
		byte[] arr = mdInst.digest();
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < arr.length; ++i) {
			sb.append(Integer.toHexString((arr[i] & 0xFF) | 0x100).substring(1, 3));
		}
		return sb.toString();
	}
	
}