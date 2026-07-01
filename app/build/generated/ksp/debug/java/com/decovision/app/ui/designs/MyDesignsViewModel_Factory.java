package com.decovision.app.ui.designs;

import com.decovision.app.data.repository.DesignRepository;
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
public final class MyDesignsViewModel_Factory implements Factory<MyDesignsViewModel> {
  private final Provider<DesignRepository> repositoryProvider;

  public MyDesignsViewModel_Factory(Provider<DesignRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public MyDesignsViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static MyDesignsViewModel_Factory create(Provider<DesignRepository> repositoryProvider) {
    return new MyDesignsViewModel_Factory(repositoryProvider);
  }

  public static MyDesignsViewModel newInstance(DesignRepository repository) {
    return new MyDesignsViewModel(repository);
  }
}
