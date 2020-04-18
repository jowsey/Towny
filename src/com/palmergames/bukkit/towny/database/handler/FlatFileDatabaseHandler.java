package com.palmergames.bukkit.towny.database.handler;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Saveable;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyWorld;
import com.palmergames.bukkit.towny.utils.ReflectionUtil;
import com.palmergames.util.FileMgmt;
import org.apache.commons.lang.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FlatFileDatabaseHandler extends DatabaseHandler {
	
	@Override
	public void save(Saveable obj) {
		// Validation safety
		Validate.notNull(obj);
		Validate.notNull(obj.getSavePath(), "You must specify a save path for class: " + obj.getClass().getName());
		
		HashMap<String, String> saveMap = new HashMap<>();

		// Get field data.
		convertMapData(getObjectMap(obj), saveMap);
		
		// Add save getter data.
		convertMapData(getSaveGetterData(obj), saveMap);

		TownyMessaging.sendErrorMsg(obj.getSavePath().toString());
		
		// Save
		FileMgmt.mapToFile(saveMap, obj.getSavePath());
	}

	@Override
	@Nullable
	public <T> T load(File file, @NotNull Class<T> clazz) {
		Constructor<T> objConstructor = null;
		try {
			objConstructor = clazz.getConstructor(UUID.class);
		} catch (NoSuchMethodException e) {
			TownyMessaging.sendErrorMsg("flag 1");
			e.printStackTrace();
		}

		T obj = null;
		try {
			Validate.isTrue(objConstructor != null);
			obj = objConstructor.newInstance((Object) null);
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
			TownyMessaging.sendErrorMsg("flag 2");
			e.printStackTrace();
		}

		Validate.isTrue(obj != null);
		List<Field> fields = ReflectionUtil.getAllFields(obj, true);

		HashMap<String, String> values = loadFileIntoHashMap(file);
		for (Field field : fields) {
			Type type = field.getGenericType();
			Class<?> classType = field.getType();
			field.setAccessible(true);

			String fieldName = field.getName();

			if (values.get(fieldName) == null) {
				continue;
			}

			Object value = null;

			if (isPrimitive(type)) {
				value = loadPrimitive(values.get(fieldName), type);
			} else if (field.getType().isEnum()) {
				value = loadEnum(values.get(fieldName), classType);
			} else {
				value = fromFileString(values.get(fieldName), type);
			}

			if (value == null) {
				// ignore it as another already allocated value may be there.
				continue;
			}

			LoadSetter loadSetter = field.getAnnotation(LoadSetter.class);

			try {

				if (loadSetter != null) {
					Method method = obj.getClass().getMethod(loadSetter.setterName(), field.getType());
					method.invoke(obj, value);
				} else {
					field.set(obj, value);
				}
				
			} catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
				TownyMessaging.sendErrorMsg("flag 3");
				e.printStackTrace();
				return null;
			}
		}
		
		return obj;
	}
	
	private <T extends Enum<T>> @NotNull T loadEnum(String str, Class<?> type) {
		return Enum.valueOf((Class<T>)type, str);
	}
	
	// ---------- File Getters ----------
	
	public File getResidentFile(UUID id) {
		return new File(Towny.getPlugin().getDataFolder() + "/data/residents/" + id + ".txt");
	}
	
	public File getTownFile(UUID id) {
		return new File(Towny.getPlugin().getDataFolder() + "/data/towns/" + id + ".txt");
	}
	
	public File getNationFile(UUID id) {
		return new File(Towny.getPlugin().getDataFolder() + "/data/nations/" + id + ".txt");
	}
	
	public File getWorldFile(UUID id) {
		return new File(Towny.getPlugin().getDataFolder() + "/data/worlds/" + id + ".txt");
	}
	
	public File getTownBlockFile(UUID id) {
		return new File(Towny.getPlugin().getDataFolder() + "/data/townblocks/" + id + ".txt");
	}

	// ---------- File Getters ----------
	
	// ---------- Loaders ----------
	
	@Override
	public Resident loadResident(UUID id) {
		File residentFileFile = getResidentFile(id);
		return load(residentFileFile, Resident.class);
	}
	
	@Override
	public Town loadTown(UUID id) {
		File townFile = getTownFile(id);
		return load(townFile, Town.class);
	}

	@Override
	public Nation loadNation(UUID id) {
		File nationFile = getNationFile(id);
		return load(nationFile, Nation.class);
	}

	@Override
	public TownyWorld loadWorld(UUID id) {
		File worldFile = getWorldFile(id);
		return load(worldFile, TownyWorld.class);
	}
	
	// ---------- Loaders ----------
	
	@Override
	public void loadAllResidents() {
		TownyMessaging.sendErrorMsg("Called");
		File resDir = new File(Towny.getPlugin().getDataFolder() + "/data/residents");
		String[] resFiles = resDir.list((dir, name) -> name.endsWith(".txt"));
		for (String fileName : resFiles) {
			String idStr = fileName.replace(".txt", "");
			UUID id = UUID.fromString(idStr);
			Resident loadedResident = loadResident(id);
			TownyMessaging.sendErrorMsg("Called 2");
			
			// Store data.
			try {
				TownyMessaging.sendErrorMsg("Called 3");
				TownyUniverse.getInstance().addResident(loadedResident);
			} catch (AlreadyRegisteredException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void loadAllWorlds() {
		File worldsDir = new File(Towny.getPlugin().getDataFolder() + "/data/worlds");
		String[] worldFiles = worldsDir.list((dir, name) -> name.endsWith(".txt"));
		TownyMessaging.sendErrorMsg(Arrays.toString(worldFiles));
		for (String fileName : worldFiles) {
			TownyMessaging.sendErrorMsg(fileName);
			String idStr = fileName.replace(".txt", "");
			UUID id = UUID.fromString(idStr);
			TownyWorld loadedWorld = loadWorld(id);
			
			if (loadedWorld == null) {
				TownyMessaging.sendErrorMsg("Could not load" + fileName);
				continue;
			}
			
			try {
				TownyUniverse.getInstance().addWorld(loadedWorld);
			} catch (AlreadyRegisteredException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void loadAllTowns() {
		File worldsDir = new File(Towny.getPlugin().getDataFolder() + "/data/towns");
		String[] townFiles = worldsDir.list((dir, name) -> name.endsWith(".txt"));
		TownyMessaging.sendErrorMsg(Arrays.toString(townFiles));
		for (String fileName : townFiles) {
			TownyMessaging.sendErrorMsg(fileName);
			String idStr = fileName.replace(".txt", "");
			UUID id = UUID.fromString(idStr);
			Town loadedTown = loadTown(id);

			if (loadedTown == null) {
				TownyMessaging.sendErrorMsg("Could not load" + fileName);
				continue;
			}

			// Store data.
			try {
				TownyUniverse.getInstance().addTown(loadedTown);
			} catch (AlreadyRegisteredException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void loadAllTownBlocks() {
		File worldsDir = new File(Towny.getPlugin().getDataFolder() + "/data/townblocks");
		String[] townblockFiles = worldsDir.list((dir, name) -> name.endsWith(".txt"));
		TownyMessaging.sendErrorMsg(Arrays.toString(townblockFiles));
		for (String fileName : townblockFiles) {
			TownyMessaging.sendErrorMsg(fileName);
			String idStr = fileName.replace(".txt", "");
			UUID id = UUID.fromString(idStr);
			TownBlock loadedTownBlock = loadTownBlock(id);

			if (loadedTownBlock == null) {
				TownyMessaging.sendErrorMsg("Could not load" + fileName);
				continue;
			}

			// Store data.
			try {
				TownyUniverse.getInstance().addTownBlock(loadedTownBlock);
			} catch (AlreadyRegisteredException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public TownBlock loadTownBlock(UUID id) {
		File townblockFile = getTownBlockFile(id);
		return load(townblockFile, TownBlock.class);
	}

	private void convertMapData(Map<String, ObjectContext> from, Map<String, String> to) {
		for (Map.Entry<String, ObjectContext> entry : from.entrySet()) {
			String valueStr = toFileString(entry.getValue().getValue(), entry.getValue().getType());
			to.put(entry.getKey(), valueStr);
		}
	}

	@Override
	public void load() {
		
	}
}