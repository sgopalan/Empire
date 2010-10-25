/*
 * Copyright (c) 2009-2010 Clark & Parsia, LLC. <http://www.clarkparsia.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.clarkparsia.empire.codegen;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewConstructor;
import javassist.CtField;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import javassist.Modifier;
import javassist.CannotCompileException;

import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collection;

import java.lang.reflect.Method;

import com.clarkparsia.empire.SupportsRdfId;
import com.clarkparsia.empire.util.BeanReflectUtil;
import static com.clarkparsia.utils.collections.CollectionUtil.find;
import com.clarkparsia.utils.Predicate;
import com.google.inject.internal.Sets;

/**
 * <p>Generate implementations of interfaces at runtime via bytecode manipulation.</p>
 *
 * @author Michael Grove
 * @since 0.5.1
 * @version 0.7
 */
public class InstanceGenerator {

	private static final Collection<Method> processedMethods = Sets.newHashSet();

	/**
	 * <p>Given a bean-style interface, generate an instance of the interface by implementing getters and setters for each
	 * property.  It will also add implementations to support the {@link SupportsRdfId} interface and generate simple,
	 * default equals, toString and hashCode methods.</p>
	 *
	 * <p>If there are other non-bean style (getter and/or setter's for properties) methods on the interface, this will
	 * likely fail to generate the instance.</p>
	 * @param theInterface the interface to build an instance of
	 * @param <T> the type of the interface
	 * @return New dynamically generated bytecode of a class that implements the given interface.
	 * @throws Exception if there is an error while generating the bytecode of the new class.
	 */
	public static <T> Class<T> generateInstanceClass(Class<T> theInterface) throws Exception {
		processedMethods.clear();

		// TODO: can we use some sort of template language for this?

		ClassPool aPool = ClassPool.getDefault();

		CtClass aInterface = aPool.get(theInterface.getName());
		CtClass aSupportsRdfIdInterface = aPool.get(SupportsRdfId.class.getName());

		if (!Arrays.asList(aInterface.getInterfaces()).contains(aSupportsRdfIdInterface)
			&& !SupportsRdfId.class.isAssignableFrom(theInterface)) {
			throw new IllegalArgumentException("Class does not implement SupportsRdfId, cannot generate Empire suitable implementation.");
		}

		String aName = aInterface.getPackageName()+ ".impl." + aInterface.getSimpleName() + "Impl";
		CtClass aClass = null;
		
		try {
			//  i had a good reason for doing this, but i dont remember what it is.  when i do, i'll explain it here =)
			
			aClass = aPool.get(aName);
			return (Class<T>) BeanReflectUtil.loadClass(aName);
		}
		catch (NotFoundException e) {
			aClass = aPool.makeClass(aInterface.getPackageName()+ ".impl." + aInterface.getSimpleName() + "Impl");
		}
		catch (ClassNotFoundException e) {
			throw new Exception("Previously created class cannot be loaded.", e);
		}

		if (aClass.isFrozen()) {
			aClass.defrost();
		}

		if (aInterface.isInterface()) {
			aClass.addInterface(aInterface);
		}
		else {
			aClass.setSuperclass(aInterface);
		}

		aClass.addInterface(aSupportsRdfIdInterface);

		aClass.addConstructor(CtNewConstructor.defaultConstructor(aClass));

		generateMethods(theInterface, aPool, aClass);
		generateMethodsForSuperInterfaces(theInterface, aPool, aClass);

		CtField aIdField = new CtField(aPool.get(SupportsRdfId.class.getName()), "supportsId", aClass);
		aClass.addField(aIdField, CtField.Initializer.byExpr("new com.clarkparsia.empire.annotation.SupportsRdfIdImpl();"));

		if (!hasMethod(aClass, "getRdfId")) {
			aClass.addMethod(CtNewMethod.make("public com.clarkparsia.empire.SupportsRdfId.RdfKey getRdfId() { return supportsId.getRdfId(); } ", aClass));
		}

		if (!hasMethod(aClass, "setRdfId")) {
			aClass.addMethod(CtNewMethod.make("public void setRdfId(com.clarkparsia.empire.SupportsRdfId.RdfKey theURI) { supportsId.setRdfId(theURI); } ", aClass));
		}

		// TODO: generate a more sophisticated equals method based on the fields in the bean
		aClass.addMethod(CtNewMethod.make("public boolean equals(Object theObj) { " +
										  "  if (theObj == this) return true;\n" +
										  "  if (!(theObj instanceof com.clarkparsia.empire.SupportsRdfId)) return false;\n" +
										  "  if (!(this.getClass().isAssignableFrom(theObj.getClass()))) return false;\n" +
										  "  return getRdfId().equals( ((com.clarkparsia.empire.SupportsRdfId) theObj).getRdfId());" +
										  "} ", aClass));

		aClass.addMethod(CtNewMethod.make("public String toString() { return getRdfId() != null ? getRdfId().toString() : super.toString(); } ", aClass));
		aClass.addMethod(CtNewMethod.make("public int hashCode() { return getRdfId() != null ? getRdfId().hashCode() : 0; } ", aClass));

		aClass.freeze();

		Class<T> aResult = (Class<T>) aClass.toClass();

		try {
			// make sure this is a valid class, that is, we can create instances of it!
			aResult.newInstance();
		}
		catch (Exception ex) {
			// TODO: log this?
			throw ex;
		}

		return aResult;
	}

	/**
	 * For all the parent interfaces of a class, generate implementations of all their methods.  And for their parents, do the same, and the same for their parents, and so on...
	 * @param theInterface the interface
	 * @param thePool the class pool to use
	 * @param theCtClass the concrete implementation of the interface(s)
	 * @param <T> the type of the interface
	 * @throws NotFoundException thrown if there is an error generating the methods
	 * @throws CannotCompileException thrown if there is an error generating the methods
	 */
	private static <T> void generateMethodsForSuperInterfaces(final Class<T> theInterface, ClassPool thePool, CtClass theCtClass) throws NotFoundException, CannotCompileException {
		if (theInterface.getSuperclass() != null) {
			generateMethods(theInterface.getSuperclass(), thePool, theCtClass);
			generateMethodsForSuperInterfaces(theInterface.getSuperclass(), thePool, theCtClass);
		}
		
		for (Class<?> aSuperInterface : theInterface.getInterfaces()) {
			generateMethods(aSuperInterface, thePool, theCtClass);

			generateMethodsForSuperInterfaces(aSuperInterface, thePool, theCtClass);
		}
	}

	/**
	 * For a given interface, generate basic getter and setter methods for all the properties on the interface.
	 * @param theInterface the interface
	 * @param thePool the class pool
	 * @param theClass the concrete implementation of the interface
	 * @param <T> the type of the interface
	 * @throws CannotCompileException thrown if there is an error generating the methods
	 * @throws NotFoundException thrown if there is an error generating the methods
	 */
	private static <T> void generateMethods(final Class<T> theInterface, final ClassPool thePool, final CtClass theClass) throws CannotCompileException, NotFoundException {
		Map<String, Class> aProps = properties(theInterface);

		for (String aProp : aProps.keySet()) {
			CtField aNewField = new CtField(thePool.get(aProps.get(aProp).getName()), aProp, theClass);

			if (!hasField(theClass, aNewField.getName())) {
				theClass.addField(aNewField);
			}

			if (!hasMethod(theClass, getterName(aProp))) {
				theClass.addMethod(CtNewMethod.getter(getterName(aProp), aNewField));
			}

			if (!hasMethod(theClass, setterName(aProp))) {
				theClass.addMethod(CtNewMethod.setter(setterName(aProp), aNewField));
			}
		}
	}

	/**
	 * Return whether or not the class has a field with the given name
	 * @param theClass the class to inspect
	 * @param theField the name of the field to look for
	 * @return true if the class contains the field, false otherwise
	 */
	private static boolean hasField(CtClass theClass, String theField) {
		try {
			return theClass.getDeclaredField(theField) != null;
		}
		catch (NotFoundException e) {
			return false;
		}
	}

	/**
	 * Return whether or not the class has a method with the given name
	 * @param theClass the class to inspect
	 * @param theName the name of the method to look for
	 * @return true if the class contains the method, false otherwise
	 */
	private static boolean hasMethod(CtClass theClass, String theName) {
		try {
			return theClass.getDeclaredMethod(theName) != null &&
				   !Modifier.isAbstract(theClass.getDeclaredMethod(theName).getModifiers());
		}
		catch (NotFoundException e) {
			try {
				if (theClass.getSuperclass() != null) {
					return hasMethod(theClass.getSuperclass(), theName);
				}
				else {
					return false;
				}
			}
			catch (NotFoundException e1) {
				return false;
			}
		}
	}

	/**
	 * Reurn the name of the getter method given the bean property name.  For example, if there is a property "name"
	 * this will return "getName"
	 * @param theProperyName the bean property name
	 * @return the name of the getter for the property
	 */
	private static String getterName(String theProperyName) {
		return "get" + String.valueOf(theProperyName.charAt(0)).toUpperCase() + theProperyName.substring(1);
	}

	/**
	 * Return the name of the setter method given the bean property name.  For example, if there is a property "name"
	 * this will return "setName"
	 * @param theProperyName the bean property name
	 * @return the setter name for the bean property
	 */
	private static String setterName(String theProperyName) {
		return "set" + String.valueOf(theProperyName.charAt(0)).toUpperCase() + theProperyName.substring(1);
	}

	/**
	 * Get the bean proeprties from the given class
	 * @param theClass the bean class
	 * @return a Map of the bean property names with the type the property as the value
	 */
	private static Map<String, Class> properties(Class theClass) {
		Map<String, Class> aMap = new HashMap<String, Class>();

		for (Method aMethod : theClass.getDeclaredMethods()) {
			FINDER.method = aMethod;
			if (find(processedMethods, FINDER)) {
				continue;
			}

			// we want to ignore methods with implementations, we should not override them.
			if (!Modifier.isAbstract(aMethod.getModifiers())) {
				processedMethods.add(aMethod);
				continue;
			}

			String aProp = aMethod.getName().substring(aMethod.getName().startsWith("is") ? 2 : 3);

			aProp = String.valueOf(aProp.charAt(0)).toLowerCase() + aProp.substring(1);

			if (!aMethod.getName().startsWith("get")
				&& !aMethod.getName().startsWith("is")
				&& !aMethod.getName().startsWith("has")
				&& !aMethod.getName().startsWith("set")) {

				throw new IllegalArgumentException("Non-bean style methods found, implementations for them cannot not be generated.  Method was: " + aMethod);
			}

			Class aType = null;

			if (aMethod.getName().startsWith("get") || aMethod.getName().startsWith("is") || aMethod.getName().startsWith("has")) {
				aType = aMethod.getReturnType();
			}
			else if (aMethod.getName().startsWith("set") && aMethod.getParameterTypes().length > 0) {
				aType = aMethod.getParameterTypes()[0];
			}

			if (aType != null) {
				aMap.put(aProp, aType);
			}

			processedMethods.add(aMethod);
		}

		return aMap;
	}

	private static final FinderPredicate FINDER = new FinderPredicate();

	private static class FinderPredicate implements Predicate<Method> {
		Method method;

		public boolean accept(final Method theValue) {
			return overrideEquals(theValue, method);
		}
	}

	/**
	 * Basically a copy of Method.equals, but rather than enforcing a strict equals, it tests for "overridable" equals.  So this will
	 * return true if the methods are .equals or if the second method can override the first.
	 * @param obj
	 * @param other
	 * @return
	 */
	private static boolean overrideEquals(Method obj, Method other) {
		if ((other.getDeclaringClass().isAssignableFrom(obj.getDeclaringClass())) && (obj.getName().equals(other.getName()))) {
			if (!obj.getReturnType().equals(other.getReturnType())) {
				return false;
			}

			Class[] params1 = obj.getParameterTypes();
			Class[] params2 = other.getParameterTypes();
			if (params1.length == params2.length) {
				for (int i = 0; i < params1.length; i++) {
					if (params1[i] != params2[i])
						return false;
				}
			}
			
			return true;
		}
		else {
			return false;
		}
	}
}
