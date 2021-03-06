package org.codehaus.groovy.grails.plugins.camel

import grails.plugins.camel.ClosureProcessor
import grails.plugins.camel.ClosurePredicate
import org.apache.camel.builder.RouteBuilder
import org.slf4j.LoggerFactory

class GrailsRouteBuilderConfigureHelper {

	static log = LoggerFactory.getLogger(this)
	
	private builder
	private definition
	private node
	
	static configure(RouteBuilder builder, Closure definition) {
		configure(builder, null, definition)
	}
	
	static configure(RouteBuilder builder, Object startingNode, Closure definition) {
		log.debug("building {builder: '$builder', startingNode: '$startingNode', definition: '${definition.toString()}'}")
		new GrailsRouteBuilderConfigureHelper(builder, startingNode, definition).node
	}
	
	private GrailsRouteBuilderConfigureHelper(RouteBuilder builder, Object startingNode, Closure definition) {
		this.builder = builder
		this.node = startingNode
		this.definition = definition
		
		this.definition.delegate = this
		this.definition.resolveStrategy = Closure.DELEGATE_FIRST
		node = this.definition()
	}
		
	def methodMissing(String name, args) {
		log.debug("invoking $name($args) {node = $node}")
		
		args.eachWithIndex { arg, i ->
			if (arg instanceof Closure) {
				args[i] = new ClosurePredicate(arg)
			}
		}

		def argClasses = args.collect { it.class } as Object[]

		def resolved = [node: node, builder: builder].find { targetName, target ->
			if (target == null) return false
			
			log.debug("resolving against '$targetName' (${target.class})")
			def metaMethod = target.metaClass.getMetaMethod(name, argClasses)
			if (metaMethod) {
				log.debug("$targetName DOES respond")
				node = metaMethod.invoke(target, args)
				true
			} else {
				log.debug("$targetName DOES NOT respond")
				false
			}
		}

		if (!resolved) {
			log.error("could not resolve $name($args)")
			throw new MissingMethodException(name, definition.owner.class, args)
		}
	
		node
	}

	def choice() {
		methodMissing("choice", [] as Object[])
	}
	
	def choice(Closure choiceDefinition) {
		log.debug("invoking choice with ${choiceDefinition.toString()}")
		choice()
		node = configure(builder, node, choiceDefinition)
		end()
	}

	def when(predicate, Closure whenDefinition) {
		log.debug("invoking when with ${predicate.toString()} and ${whenDefinition.toString()}")
		when(predicate)
		node = configure(builder, node, whenDefinition)
		node
	}
	
	def otherwise() {
		methodMissing("otherwise", [] as Object[])
	}

	def otherwise(Closure otherwiseDefinition) {
		log.debug("invoking otherwise with ${otherwiseDefinition.toString()}")
		otherwise()
		node = configure(builder, node, otherwiseDefinition)
	}
	
	def process(Closure processor) {
		process(new ClosureProcessor(processor))
	}
	
	def onException(Class exceptionClass, Closure onExceptionDefinition) {
		log.debug("invoking onException($exceptionClass) with ${onExceptionDefinition.toString()}")
		onException(exceptionClass)
		node = configure(builder, node, onExceptionDefinition)
		end()
	} 
	
}