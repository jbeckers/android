/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.sync

import com.android.ide.common.gradle.model.IdeVariantHeader
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet.Companion.getInstance
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel.Companion.get
import com.android.utils.appendCamelCase
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.io.IOException
import java.io.Serializable

data class SelectedVariant(
  /**
   * The id of the module in the form returned by [Modules.createUniqueModuleId].
   */
  val moduleId: String,

  val variantName: String,

  val abiName: String?,

  /**
   * An instance of [VariantDetails] which describes [variantName]. `null` if no models were available when constructing this instance.
   */
  val details: VariantDetails?
) : Serializable

data class VariantDetails(
  val name: String,
  val buildType: String,

  /**
   * Dimension name to flavor name pairs in the dimension order. emptyList() if there is no flavors or dimensions defined.
   */
  val flavors: List<Pair<String, String>>
) : Serializable

data class SelectedVariants(
  /**
   * Dimension name to selected variant name map.
   */
  val selectedVariants: Map<String, SelectedVariant>
) : Serializable {
  fun getSelectedVariant(moduleId: String): String? = selectedVariants[moduleId]?.variantName
  fun getSelectedAbi(moduleId: String): String? = selectedVariants[moduleId]?.abiName
}

data class VariantSelectionChange(
  /**
   * The name of the build type in the diffed variant if different from the build type name in the base variant.
   */
  val buildType: String? = null,

  /**
   * Pairs of the dimension and flavor names which are different in the diffed variant when compared with the base variant.
   */
  val flavors: Map<String, String> = emptyMap()
) {
  val isEmpty: Boolean get() = buildType == null && flavors.isEmpty()

  companion object {
    val EMPTY = VariantSelectionChange()

    /**
     * Extracts the dimensions and values that differ between two compatible variants [base] and [from].
     */
    fun extractVariantSelectionChange(from: VariantDetails, base: VariantDetails?): VariantSelectionChange? {
      // We cannot process variant changes when variant definitions changed.
      if (from.flavors.map { it.first } != base?.flavors?.map { it.first }) return null

      val otherFlavors = base.flavors.toMap()
      return VariantSelectionChange(
        buildType = from.buildType.takeUnless { it == base.buildType },
        flavors = from.flavors.filter { (dimension, newFlavor) -> otherFlavors[dimension] != newFlavor }.toMap()
      )
    }
  }
}

class SelectedVariantCollector(private val project: Project) {

  fun collectSelectedVariants(): SelectedVariants {
    return SelectedVariants(
      ModuleManager.getInstance(project).modules.mapNotNull { module -> findSelectedVariant(module) }.associateBy { it.moduleId }
    )
  }

  private fun findSelectedVariant(module: Module): SelectedVariant? {
    val gradleFacet = GradleFacet.getInstance(module)
    if (gradleFacet != null) {
      val moduleId = gradleFacet.getModuleId() ?: return null
      val androidFacet = AndroidFacet.getInstance(module) ?: return null
      val androidModuleModel = AndroidModuleModel.get(androidFacet)
      val variantDetails = androidModuleModel?.getSelectedVariantDetails()
      val ndkModuleModel = get(module)
      val ndkFacet = getInstance(module)
      if (ndkFacet != null && ndkModuleModel != null) {
        // Note, we lose ABI selection if cached models are not available.
        val (variant, abi) = ndkFacet.selectedVariantAbi ?: return null
        return SelectedVariant(moduleId, variant, abi, variantDetails)
      }
      return SelectedVariant(moduleId, androidFacet.properties.SELECTED_BUILD_VARIANT, null, variantDetails)
    }
    return null
  }

  private fun AndroidModuleModel.getSelectedVariantDetails(): VariantDetails? {
    val selectedVariant = try {
      selectedVariant
    }
    catch (e: Exception) {
      Logger.getInstance(SelectedVariantCollector::class.java).error("Selected variant is not available for: $moduleName", e)
      return null
    }
    return createVariantDetailsFrom(androidProject.flavorDimensions, selectedVariant)
  }

  private fun GradleFacet.getModuleId(): String? {
    val rootProjectPath: String
    rootProjectPath = try {
      val path = ExternalSystemApiUtil.getExternalRootProjectPath(module) ?: return null
      File(path).canonicalPath
    }
    catch (e: IOException) {
      Logger.getInstance(SelectedVariantCollector::class.java).error(e)
      return null
    }
    val rootFolder = File(rootProjectPath)
    val projectPath = configuration.GRADLE_PROJECT_PATH
    return Modules.createUniqueModuleId(rootFolder, projectPath)
  }
}

fun createVariantDetailsFrom(dimensions: Collection<String>, variant: IdeVariantHeader): VariantDetails =
  VariantDetails(
    variant.name,
    variant.buildType,
    if (dimensions.size == variant.productFlavors.size) dimensions.zip(variant.productFlavors) else emptyList()
  )

fun VariantDetails.applyChange(selectionChange: VariantSelectionChange): VariantDetails {
  val newBuildType = selectionChange.buildType ?: buildType
  val newFlavors = flavors.map { (dimension, flavor) -> dimension to (selectionChange.flavors[dimension] ?: flavor) }
  return VariantDetails(
    buildVariantName(newBuildType, newFlavors.asSequence().map { it.second }),
    newBuildType,
    newFlavors
  )
}

fun buildVariantName(buildType: String, flavors: Sequence<String>): String {
  return buildString {
    flavors.forEach { appendCamelCase(it) }
    appendCamelCase(buildType)
  }
}

fun VariantSelectionChange.format(base: VariantDetails?): String = buildString {
  val baseFlavors = base?.flavors?.toMap().orEmpty()
  if (buildType != null) {
    append(base?.buildType.orEmpty())
    append(" => ")
    append(buildType)
    append('\n')
  }
  flavors.forEach { (dimension, flavor) ->
    append(baseFlavors[dimension].orEmpty())
    append('@')
    append(dimension)
    append(" => ")
    append(flavor)
    append('@')
    append(dimension)
    append('\n')
  }
}