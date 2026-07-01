package com.decovision.app.data.repository;

import com.decovision.app.data.db.DesignDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class DesignRepository_Factory implements Factory<DesignRepository> {
  private final Provider<DesignDao> daoProvider;

  public DesignRepository_Factory(Provider<DesignDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public DesignRepository get() {
    return newInstance(daoProvider.get());
  }

  public static DesignRepository_Factory create(Provider<DesignDao> daoProvider) {
    return new DesignRepository_Factory(daoProvider);
  }

  public static DesignRepository newInstance(DesignDao dao) {
    return new DesignRepository(dao);
  }
}
