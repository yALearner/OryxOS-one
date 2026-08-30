package com.oryxos.core;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Profile 内存索引：按 name 快速查找。启动扫描是当前唯一注册路径， 后续节补运行时 {@code register()} 的场景（当前方法即预留入口）。 */
public final class ProfileRegistry {

  private final Map<String, Profile> profiles = new ConcurrentHashMap<>();

  public void register(Profile profile) {
    profiles.put(profile.name(), profile);
  }

  public Optional<Profile> findByName(String name) {
    return Optional.ofNullable(profiles.get(name));
  }

  public Collection<Profile> list() {
    return List.copyOf(profiles.values());
  }
}
