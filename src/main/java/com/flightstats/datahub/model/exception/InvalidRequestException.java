package com.flightstats.datahub.model.exception;

public class InvalidRequestException extends Exception
{
	public InvalidRequestException( String message )
	{
		super( message );
	}

	public InvalidRequestException( String message, Throwable t )
	{
		super( message, t );
	}
}
