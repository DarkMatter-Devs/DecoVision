package com.decovision.app.di;

import com.decovision.app.data.db.DesignDao;
import com.decovision.app.data.repository.DesignRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class DatabaseModule_ProvideDesignRepositoryFactory implements Factory<DesignRepository> {
  private final Provider<DesignDao> daoProvider;

  public DatabaseModule_ProvideDesignRepositoryFactory(Provider<DesignDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public DesignRepository get() {
    return provideDesignRepository(daoProvider.get());
  }

  public static DatabaseModule_ProvideDesignRepositoryFactory create(
      Provider<DesignDao> daoProvider) {
    return new DatabaseModule_ProvideDesignRepositoryFactory(daoProvider);
  }

  public static DesignRepository provideDesignRepository(DesignDao dao) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideDesignRepository(dao));
  }
}
