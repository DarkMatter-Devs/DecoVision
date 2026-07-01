package com.decovision.app.ui.ar;

import com.decovision.app.data.repository.DesignRepository;
import com.google.gson.Gson;
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
public final class ArViewModel_Factory implements Factory<ArViewModel> {
  private final Provider<DesignRepository> repositoryProvider;

  private final Provider<Gson> gsonProvider;

  public ArViewModel_Factory(Provider<DesignRepository> repositoryProvider,
      Provider<Gson> gsonProvider) {
    this.repositoryProvider = repositoryProvider;
    this.gsonProvider = gsonProvider;
  }

  @Override
  public ArViewModel get() {
    return newInstance(repositoryProvider.get(), gsonProvider.get());
  }

  public static ArViewModel_Factory create(Provider<DesignRepository> repositoryProvider,
      Provider<Gson> gsonProvider) {
    return new ArViewModel_Factory(repositoryProvider, gsonProvider);
  }

  public static ArViewModel newInstance(DesignRepository repository, Gson gson) {
    return new ArViewModel(repository, gson);
  }
}
