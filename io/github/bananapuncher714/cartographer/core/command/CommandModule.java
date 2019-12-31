package io.github.bananapuncher714.cartographer.core.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import io.github.bananapuncher714.cartographer.core.Cartographer;
import io.github.bananapuncher714.cartographer.core.module.Module;

public class CommandModule implements CommandExecutor, TabCompleter {
	private Cartographer plugin;
	
	protected CommandModule( Cartographer plugin ) {
		this.plugin = plugin;
	}
	
	@Override
	public List< String > onTabComplete( CommandSender sender, Command arg1, String arg2, String[] args ) {
		List< String > aos = new ArrayList< String >();
		if ( !sender.hasPermission( "cartographer.admin" ) ) {
			return aos;
		}
		
		if ( args.length == 1 ) {
			aos.add( "list" );
			aos.add( "reload" );
			aos.add( "enable" );
			aos.add( "disable" );
		} else if ( args.length == 2 ) {
			if ( args[ 0 ].equalsIgnoreCase( "reload" ) ) {
				for ( Module module : plugin.getModuleManager().getModules() ) {
					aos.add( module.getName() );
				}
			} else if ( args[ 0 ].equalsIgnoreCase( "enable" ) ) {
				for ( Module module : plugin.getModuleManager().getModules() ) {
					if ( !module.isEnabled() ) {
						aos.add( module.getName() );
					}
				}
			} else if ( args[ 0 ].equalsIgnoreCase( "disable" ) ) {
				for ( Module module : plugin.getModuleManager().getModules() ) {
					if ( module.isEnabled() ) {
						aos.add( module.getName() );
					}
				}
			}
		}
		
		List< String > completions = new ArrayList< String >();
		StringUtil.copyPartialMatches( args[ args.length - 1 ], aos, completions );
		Collections.sort( completions );
		return completions;
	}

	@Override
	public boolean onCommand( CommandSender sender, Command arg1, String arg2, String[] args ) {
		try {
			if ( args.length == 0 ) {
				sender.sendMessage( ChatColor.RED + "Usage: /cartographer module <list|reload|enable|disable> ..." );
			} else if ( args.length > 0 ) {
				String option = args[ 0 ];
				args = CommandCartographer.pop( args );
				if ( option.equalsIgnoreCase( "list" ) ) {
					list( sender, args );
				} else if ( option.equalsIgnoreCase( "reload" ) ) {
					reload( sender, args );
				} else if ( option.equalsIgnoreCase( "enable" ) ) {
					enable( sender, args );
				} else if ( option.equalsIgnoreCase( "disable" ) ) {
					disable( sender, args );
				} else {
					sender.sendMessage( ChatColor.RED + "Usage: /cartographer module <list|reload|enable|disable> ..." );
				}
			}
		} catch ( IllegalArgumentException exception ) {
			sender.sendMessage( exception.getMessage() );
		}
		return false;
	}
	
	private void list( CommandSender sender, String[] args ) {
		Validate.isTrue( sender.hasPermission( "cartographer.admin" ), ChatColor.RED + "You do not have permission to run this command!" );
		
		Set< Module > modules = plugin.getModuleManager().getModules();
		if ( modules.isEmpty() ) {
			sender.sendMessage( ChatColor.GOLD + "There are currently no modules loaded!" );
		} else {
			StringBuilder builder = new StringBuilder();
			builder.append( ChatColor.GOLD );
			builder.append( "Cartographer2 Modules (" );
			builder.append( modules.size() );
			builder.append( "): " );
			for ( Iterator< Module > iterator = modules.iterator(); iterator.hasNext(); ) {
				Module module = iterator.next();
				if ( module.isEnabled() ) {
					builder.append( ChatColor.GREEN );
				} else {
					builder.append( ChatColor.RED );
				}
				builder.append( module.getName() );
				
				if ( iterator.hasNext() ) {
					builder.append( ChatColor.GOLD );
					builder.append( ", " );
				}
			}
			sender.sendMessage( builder.toString() );
		}
	}
	
	private void reload( CommandSender sender, String[] args ) {
		Validate.isTrue( sender.hasPermission( "cartographer.admin" ), ChatColor.RED + "You do not have permission to run this command!" );
		if ( args.length == 0 ) {
			plugin.getModuleManager().disableModules();
			plugin.getModuleManager().enableModules();
			
			sender.sendMessage( ChatColor.GOLD + "Reloaded all modules!" );
		} else {
			StringBuilder builder = new StringBuilder();
			for ( String string : args  ) {
				builder.append( string );
				builder.append( " " );
			}
			String moduleName = builder.toString().trim();
			
			Module module = plugin.getModuleManager().getModule( moduleName );
			Validate.isTrue( module != null, ChatColor.RED + "'" + moduleName + "' is not a valid module!" );
			
			plugin.getModuleManager().disableModule( module );
			plugin.getModuleManager().enableModule( module );
			
			sender.sendMessage( ChatColor.GOLD + "Reloaded module '" + ChatColor.YELLOW + module.getName() + ChatColor.GOLD + "'" );
		}
	}
	
	private void enable( CommandSender sender, String[] args ) {
		Validate.isTrue( sender.hasPermission( "cartographer.admin" ), ChatColor.RED + "You do not have permission to run this command!" );
		Validate.isTrue( args.length > 0, ChatColor.RED + "Usage: /cartographer module enable <id>" );
		StringBuilder builder = new StringBuilder();
		for ( String string : args  ) {
			builder.append( string );
			builder.append( " " );
		}
		String moduleName = builder.toString().trim();
		
		Module module = plugin.getModuleManager().getModule( moduleName );
		Validate.isTrue( module != null, ChatColor.RED + "'" + moduleName + "' is not a valid module!" );
		
		if ( module.isEnabled() ) {
			sender.sendMessage( ChatColor.RED + "Module '" + moduleName + "' is already enabled!" );
			return;
		}
		
		boolean valid = plugin.getModuleManager().enableModule( module );
		
		if ( valid ) {
			sender.sendMessage( ChatColor.GOLD + "Enabled module '" + ChatColor.YELLOW + moduleName + ChatColor.GOLD + "'!" );
		} else {
			sender.sendMessage( ChatColor.RED + "Unable to load module '" + moduleName + "', Check the server log for details.(Missing dependencies?)" );
		}
	}
	
	private void disable( CommandSender sender, String[] args ) {
		Validate.isTrue( sender.hasPermission( "cartographer.admin" ), ChatColor.RED + "You do not have permission to run this command!" );
		Validate.isTrue( args.length > 0, ChatColor.RED + "Usage: /cartographer module disable <id>"  );
		StringBuilder builder = new StringBuilder();
		for ( String string : args  ) {
			builder.append( string );
			builder.append( " " );
		}
		String moduleName = builder.toString().trim();
		
		Module module = plugin.getModuleManager().getModule( moduleName );
		Validate.isTrue( module != null, ChatColor.RED + "'" + moduleName + "' is not a valid module!" );
		
		boolean valid = plugin.getModuleManager().disableModule( module );
		
		if ( valid ) {
			sender.sendMessage( ChatColor.GOLD + "Disabled module '" + ChatColor.YELLOW + moduleName + ChatColor.GOLD + "'!" );
		} else {
			sender.sendMessage( ChatColor.RED + "Module '" + moduleName + "' is already disabled!" );
		}
	}
}