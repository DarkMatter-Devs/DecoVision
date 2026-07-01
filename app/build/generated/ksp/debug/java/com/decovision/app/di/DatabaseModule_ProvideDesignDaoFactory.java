package com.decovision.app.di;

import com.decovision.app.data.db.AppDatabase;
import com.decovision.app.data.db.DesignDao;
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
public final class DatabaseModule_ProvideDesignDaoFactory implements Factory<DesignDao> {
  private final Provider<AppDatabase> dbProvider;

  public DatabaseModule_ProvideDesignDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public DesignDao get() {
    return provideDesignDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideDesignDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideDesignDaoFactory(dbProvider);
  }

  public static DesignDao provideDesignDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideDesignDao(db));
  }
}
